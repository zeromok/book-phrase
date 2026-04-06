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
 * 2. ISBN 중복 체크 → 신규 책만 처리
 * 3. Claude API → 적합성 판단 + 감성 문구 + 태그 생성 (1회 호출)
 *    - suitable=false (수험서, 문제집, 유아그림책 등) → Book 저장 없이 스킵
 *    - suitable=true  → Book + Phrase + Tag 저장 → 피드 노출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentPipelineService {

    private final AladinApiService aladinApiService;
    private final ClaudeApiService claudeApiService;
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

        int savedCount   = 0;
        int skippedCount = 0;

        for (AladinApiService.AladinBookInfo bookInfo : bestsellers) {
            try {
                int result = processBook(bookInfo, allTags, tagNames);
                if (result > 0) savedCount++;
                else skippedCount++;
            } catch (Exception e) {
                log.error("[ContentPipeline] 처리 실패 - 제목: [{}], 원인: {}",
                        bookInfo.title(), e.getMessage());
            }
        }

        log.info("[ContentPipeline] ===== 파이프라인 완료. 저장: {}개, 스킵: {}개 =====",
                savedCount, skippedCount);
        return savedCount;
    }

    /**
     * 책 한 권을 처리합니다.
     *
     * @return 1 = 저장 완료, 0 = 스킵 (중복 or 부적합)
     */
    private int processBook(
            AladinApiService.AladinBookInfo bookInfo,
            List<Tag> allTags,
            List<String> tagNames) throws InterruptedException {

        // ISBN 없는 책 스킵
        if (bookInfo.isbn13() == null || bookInfo.isbn13().isBlank()) {
            log.info("[ContentPipeline] ISBN 없음, 스킵: {}", bookInfo.title());
            return 0;
        }

        // DB 중복 체크 (이미 등록된 책)
        if (bookRepository.existsByIsbn(bookInfo.isbn13())) {
            log.info("[ContentPipeline] 이미 존재하는 책, 스킵: {} ({})",
                    bookInfo.title(), bookInfo.isbn13());
            return 0;
        }

        log.info("[ContentPipeline] 신규 책 평가 시작: {} | 카테고리: {}",
                bookInfo.title(), bookInfo.categoryName());

        // Claude API 호출: 적합성 판단 + 문구/태그 생성 (1회)
        // API 과부하 방지: 호출 전 1초 대기
        Thread.sleep(1_000);
        ClaudeApiService.ClaudeResult result = claudeApiService.evaluateAndGenerate(
                bookInfo.title(),
                bookInfo.author(),
                bookInfo.categoryName(),
                tagNames
        );

        // 부적합 판정 → Book 자체를 저장하지 않음
        if (result.isRejected()) {
            log.info("[ContentPipeline] ❌ 부적합 스킵 - [{}] 사유: {}",
                    bookInfo.title(), result.reason());
            return 0;
        }

        // 적합 판정 → Book 저장
        Book book = bookRepository.save(Book.builder()
                .title(bookInfo.title())
                .author(bookInfo.author())
                .isbn(bookInfo.isbn13())
                .coverImageUrl(bookInfo.cover())
                .aladdinUrl(bookInfo.link())
                // yes24Url은 ISBN→yes24 goodsId 매핑이 없어 생략
                .build());

        log.info("[ContentPipeline] Book 저장 완료: id={}", book.getId());

        // Phrase 생성
        Phrase phrase = Phrase.builder()
                .text(result.phrase())
                .book(book)
                .build();

        // 태그 매핑 (Claude 제안 태그 이름 → Tag 엔티티 룩업)
        for (String suggestedTagName : result.tags()) {
            allTags.stream()
                    .filter(t -> t.getName().equals(suggestedTagName))
                    .findFirst()
                    .ifPresentOrElse(
                            phrase::addTag,
                            () -> log.warn("[ContentPipeline] 알 수 없는 태그 이름: {}", suggestedTagName)
                    );
        }

        phraseRepository.save(phrase);
        log.info("[ContentPipeline] ✅ 저장 완료: [{}] → \"{}\"",
                book.getTitle(), result.phrase());

        return 1;
    }
}
