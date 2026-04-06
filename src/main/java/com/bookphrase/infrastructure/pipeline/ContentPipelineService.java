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

/**
 * AI 자동 콘텐츠 파이프라인 서비스
 *
 * 흐름:
 * 1. 알라딘 베스트셀러 N권 조회
 * 2. ISBN 중복 체크 → 이미 있으면 스킵
 * 3. [1차 필터] BookSuitabilityFilter 키워드 체크 → 부적합이면 스킵 (Claude 호출 없음)
 * 4. [2차 필터] Claude API → 적합성 판단 + 감성 문구 + 태그 생성 (1회 호출)
 *    - suitable=false → Book 저장 없이 스킵
 *    - suitable=true  → Book + Phrase + Tag 저장 → 피드 노출
 *
 * 1차 필터(키워드)가 2차 필터(Claude) 앞에 위치함으로써
 * 명백한 수험서/문제집은 API 비용 없이 걸러냅니다.
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

    /**
     * 파이프라인 실행
     *
     * @param maxBooks 가져올 베스트셀러 최대 권수 (기본 10)
     * @return 실제로 저장된 신규 콘텐츠 수
     */
    @Transactional
    public int runPipeline(int maxBooks) {
        log.info("[ContentPipeline] ===== AI 자동 파이프라인 시작 (maxBooks={}) =====", maxBooks);

        // 1. 전체 태그 이름 로드 (Claude 프롬프트에 전달)
        List<Tag> allTags = tagRepository.findAll();
        List<String> tagNames = allTags.stream().map(Tag::getName).toList();
        log.info("[ContentPipeline] 사용 가능한 태그: {}", tagNames);

        // 2. 알라딘 베스트셀러 조회
        List<AladinApiService.AladinBookInfo> bestsellers = aladinApiService.fetchBestsellers(maxBooks);
        if (bestsellers.isEmpty()) {
            log.warn("[ContentPipeline] 베스트셀러 조회 결과 없음. 파이프라인 종료.");
            return 0;
        }

        int savedCount          = 0;
        int duplicateCount      = 0;
        int keywordFilterCount  = 0;
        int claudeFilterCount   = 0;

        for (AladinApiService.AladinBookInfo bookInfo : bestsellers) {
            try {
                Result result = processBook(bookInfo, allTags, tagNames);
                switch (result) {
                    case SAVED          -> savedCount++;
                    case DUPLICATE      -> duplicateCount++;
                    case KEYWORD_FILTER -> keywordFilterCount++;
                    case CLAUDE_FILTER  -> claudeFilterCount++;
                }
            } catch (Exception e) {
                log.error("[ContentPipeline] 처리 중 예외 - 제목: [{}], 원인: {}",
                        bookInfo.title(), e.getMessage());
            }
        }

        log.info("[ContentPipeline] ===== 파이프라인 완료 =====");
        log.info("[ContentPipeline] 저장: {}개 | 중복 스킵: {}개 | 키워드 필터: {}개 | Claude 필터: {}개",
                savedCount, duplicateCount, keywordFilterCount, claudeFilterCount);
        return savedCount;
    }

    private enum Result {
        SAVED,          // 저장 완료
        DUPLICATE,      // DB에 이미 존재
        KEYWORD_FILTER, // 1차 키워드 필터로 탈락
        CLAUDE_FILTER   // 2차 Claude 판단으로 탈락
    }

    /**
     * 책 한 권을 처리합니다.
     */
    private Result processBook(
            AladinApiService.AladinBookInfo bookInfo,
            List<Tag> allTags,
            List<String> tagNames) throws InterruptedException {

        // ── 사전 검사: ISBN ────────────────────────────────────────────────
        if (bookInfo.isbn13() == null || bookInfo.isbn13().isBlank()) {
            log.info("[ContentPipeline] ISBN 없음, 스킵: {}", bookInfo.title());
            return Result.KEYWORD_FILTER;
        }

        // ── 중복 체크 ──────────────────────────────────────────────────────
        if (bookRepository.existsByIsbn(bookInfo.isbn13())) {
            log.info("[ContentPipeline] 중복 스킵: {} ({})", bookInfo.title(), bookInfo.isbn13());
            return Result.DUPLICATE;
        }

        // ── 1차 필터: 키워드 블랙리스트 (Claude API 호출 없이 걸러냄) ──────
        if (!suitabilityFilter.isSuitable(bookInfo.title())) {
            // isSuitable() 내부에서 이미 로그 출력
            return Result.KEYWORD_FILTER;
        }

        // ── 2차 필터: Claude 적합성 판단 + 문구/태그 생성 ─────────────────
        log.info("[ContentPipeline] Claude 평가 시작: [{}] | 카테고리: {}",
                bookInfo.title(), bookInfo.categoryName());

        Thread.sleep(1_000); // API 과부하 방지
        ClaudeApiService.ClaudeResult result = claudeApiService.evaluateAndGenerate(
                bookInfo.title(),
                bookInfo.author(),
                bookInfo.categoryName(),
                tagNames
        );

        if (result.isRejected()) {
            log.info("[ContentPipeline] ❌ Claude 부적합 - [{}] 사유: {}",
                    bookInfo.title(), result.reason());
            return Result.CLAUDE_FILTER;
        }

        // ── 저장: 적합 판정된 책만 Book + Phrase + Tag 저장 ───────────────
        Book book = bookRepository.save(Book.builder()
                .title(bookInfo.title())
                .author(bookInfo.author())
                .isbn(bookInfo.isbn13())
                .coverImageUrl(bookInfo.cover())
                .aladdinUrl(bookInfo.link())
                // yes24Url: ISBN→goodsId 매핑 불가로 생략 (관리자가 수동 추가 가능)
                .build());

        Phrase phrase = Phrase.builder()
                .text(result.phrase())
                .book(book)
                .build();

        for (String tagName : result.tags()) {
            allTags.stream()
                    .filter(t -> t.getName().equals(tagName))
                    .findFirst()
                    .ifPresentOrElse(
                            phrase::addTag,
                            () -> log.warn("[ContentPipeline] 알 수 없는 태그: {}", tagName)
                    );
        }

        phraseRepository.save(phrase);
        log.info("[ContentPipeline] ✅ 저장 완료: [{}] → \"{}\"",
                book.getTitle(), result.phrase());

        return Result.SAVED;
    }
}
