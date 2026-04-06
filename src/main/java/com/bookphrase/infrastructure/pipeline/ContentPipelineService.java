package com.bookphrase.infrastructure.pipeline;

import com.bookphrase.domain.book.entity.Book;
import com.bookphrase.domain.book.repository.BookRepository;
import com.bookphrase.domain.phrase.entity.Phrase;
import com.bookphrase.domain.phrase.repository.PhraseRepository;
import com.bookphrase.domain.tag.entity.Tag;
import com.bookphrase.domain.tag.repository.TagRepository;
import com.bookphrase.infrastructure.aladin.AladinApiService;
import com.bookphrase.infrastructure.claude.ClaudeApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 자동 콘텐츠 파이프라인 서비스
 *
 * [Phase 1 - 초기 데이터 수집]
 *   Admin API (POST /admin/pipeline/category)로 카테고리/소스별 수동 실행
 *   다양한 장르에서 한 번에 많은 책을 수집합니다.
 *
 * [Phase 2 - 운영 자동화]
 *   Scheduler가 매일 KST 06:00에 runPipeline() 자동 실행
 *   베스트셀러 기준으로 신규 책이 있으면 추가합니다.
 *
 * [필터 구조 - 책 1권당 처리 순서]
 *   ISBN 없음         → 즉시 스킵
 *   DB 중복           → 즉시 스킵
 *   1차 키워드 필터   → 수험서/문제집 등 명백한 부적합 (Claude 호출 없음, 무료)
 *   2차 Claude 필터   → 애매한 케이스 (요리책, 여행가이드 등) + 문구/태그 생성
 *   저장              → Book + Phrase + Tag DB 저장 → 피드 노출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentPipelineService {

    private final AladinApiService aladinApiService;
    private final ClaudeApiService claudeApiService;
    private final BookSuitabilityFilter suitabilityFilter;
    private final BookRepository bookRepository;
    private final PhraseRepository phraseRepository;
    private final TagRepository tagRepository;

    // ── 스케줄러 기본 실행 (전체 베스트셀러) ─────────────────────────────
    @Transactional
    public PipelineResult runPipeline(int maxBooks) {
        log.info("[ContentPipeline] ===== 베스트셀러 파이프라인 시작 (maxBooks={}) =====", maxBooks);
        List<AladinApiService.AladinBookInfo> books = aladinApiService.fetchBestsellers(maxBooks);
        return process(books);
    }

    // ── 카테고리 지정 실행 (초기 데이터 수집용) ──────────────────────────
    @Transactional
    public PipelineResult runByCategory(
            AladinApiService.QueryType queryType,
            Integer categoryId,
            int maxBooks) {

        log.info("[ContentPipeline] ===== 카테고리 파이프라인 시작 - queryType={}, categoryId={}, maxBooks={} =====",
                queryType, categoryId, maxBooks);
        List<AladinApiService.AladinBookInfo> books =
                aladinApiService.fetchByCategory(queryType, categoryId, maxBooks);
        return process(books);
    }

    // ── 공통 처리 로직 ────────────────────────────────────────────────────
    private PipelineResult process(List<AladinApiService.AladinBookInfo> books) {
        if (books.isEmpty()) {
            log.warn("[ContentPipeline] 조회된 책 없음. 파이프라인 종료.");
            return PipelineResult.empty();
        }

        List<Tag> allTags   = tagRepository.findAll();
        List<String> tagNames = allTags.stream().map(Tag::getName).toList();
        log.info("[ContentPipeline] 사용 가능한 태그: {} | 처리 대상: {}권", tagNames, books.size());

        int saved = 0, duplicate = 0, keywordFiltered = 0, claudeFiltered = 0, error = 0;
        AtomicInteger claudeCalls = new AtomicInteger(0);

        for (AladinApiService.AladinBookInfo bookInfo : books) {
            try {
                switch (processBook(bookInfo, allTags, tagNames, claudeCalls)) {
                    case SAVED          -> saved++;
                    case DUPLICATE      -> duplicate++;
                    case KEYWORD_FILTER -> keywordFiltered++;
                    case CLAUDE_FILTER  -> claudeFiltered++;
                }
            } catch (Exception e) {
                error++;
                log.error("[ContentPipeline] 처리 중 예외 - [{}]: {}", bookInfo.title(), e.getMessage());
            }
        }

        PipelineResult result = new PipelineResult(saved, duplicate, keywordFiltered, claudeFiltered, error);
        log.info("[ContentPipeline] ===== 완료 - 저장: {}개 | 중복: {}개 | 키워드필터: {}개 | Claude필터: {}개 | 예외: {}개 | Claude호출: {}회 =====",
                saved, duplicate, keywordFiltered, claudeFiltered, error, claudeCalls.get());

        // 비용 가시성: 일일 Claude 호출이 기준치를 초과하면 경고
        if (claudeCalls.get() >= 15) {
            log.warn("[ContentPipeline] ⚠️ Claude 호출 {}회 — 비용 점검 권장 (일일 권장: 15회 이하)", claudeCalls.get());
        }

        return result;
    }

    private enum BookResult {
        SAVED, DUPLICATE, KEYWORD_FILTER, CLAUDE_FILTER
    }

    private BookResult processBook(
            AladinApiService.AladinBookInfo bookInfo,
            List<Tag> allTags,
            List<String> tagNames,
            AtomicInteger claudeCalls) throws InterruptedException {

        // ISBN 없음
        if (bookInfo.isbn13() == null || bookInfo.isbn13().isBlank()) {
            log.info("[ContentPipeline] ISBN 없음, 스킵: {}", bookInfo.title());
            return BookResult.KEYWORD_FILTER;
        }

        // DB 중복
        if (bookRepository.existsByIsbn(bookInfo.isbn13())) {
            log.info("[ContentPipeline] 중복 스킵: {}", bookInfo.title());
            return BookResult.DUPLICATE;
        }

        // 1차 키워드 필터 (Claude 호출 없이 즉시 판단)
        if (!suitabilityFilter.isSuitable(bookInfo.title())) {
            return BookResult.KEYWORD_FILTER;
        }

        // 2차 Claude 필터 + 문구/태그 생성
        log.info("[ContentPipeline] Claude 평가: [{}] | 카테고리: {}", bookInfo.title(), bookInfo.categoryName());
        Thread.sleep(1_000); // API Rate limit 방지

        claudeCalls.incrementAndGet();
        ClaudeApiService.ClaudeResult claudeResult = claudeApiService.evaluateAndGenerate(
                bookInfo.title(), bookInfo.author(), bookInfo.categoryName(), tagNames);

        if (claudeResult.isRejected()) {
            log.info("[ContentPipeline] ❌ Claude 부적합: [{}] - {}", bookInfo.title(), claudeResult.reason());
            return BookResult.CLAUDE_FILTER;
        }

        // 저장
        Book book = bookRepository.save(Book.builder()
                .title(bookInfo.title())
                .author(bookInfo.author())
                .isbn(bookInfo.isbn13())
                .coverImageUrl(bookInfo.cover())
                .aladdinUrl(bookInfo.link())
                .build());

        Phrase phrase = Phrase.builder()
                .text(claudeResult.phrase())
                .book(book)
                .build();

        claudeResult.tags().forEach(tagName ->
                allTags.stream()
                        .filter(t -> t.getName().equals(tagName))
                        .findFirst()
                        .ifPresentOrElse(phrase::addTag,
                                () -> log.warn("[ContentPipeline] 알 수 없는 태그: {}", tagName))
        );

        phraseRepository.save(phrase);
        log.info("[ContentPipeline] ✅ 저장: [{}] → \"{}\"", book.getTitle(), claudeResult.phrase());
        return BookResult.SAVED;
    }

    // ── 결과 DTO ─────────────────────────────────────────────────────────
    public record PipelineResult(int saved, int duplicate, int keywordFiltered, int claudeFiltered, int error) {
        public static PipelineResult empty() {
            return new PipelineResult(0, 0, 0, 0, 0);
        }
        public int total() {
            return saved + duplicate + keywordFiltered + claudeFiltered + error;
        }
    }
}
