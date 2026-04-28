package com.bookphrase.infrastructure.pipeline;

import com.bookphrase.domain.book.entity.Book;
import com.bookphrase.domain.phrase.entity.Phrase;
import com.bookphrase.domain.phrase.repository.PhraseRepository;
import com.bookphrase.domain.tag.entity.Tag;
import com.bookphrase.domain.tag.repository.TagRepository;
import com.bookphrase.infrastructure.aladin.AladinApiService;
import com.bookphrase.infrastructure.claude.ClaudeApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 기존에 저장된 phrase의 태그를 새 프롬프트로 다시 부여합니다 (일회성 운영 작업).
 *
 * Phrase 텍스트와 Book은 그대로 두고, phrase_tags 연결만 갱신합니다.
 * Aladin에서 카테고리를 가져와 Claude 프롬프트의 정확도를 높입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhraseRetagService {

    private final PhraseRepository phraseRepository;
    private final TagRepository tagRepository;
    private final AladinApiService aladinApiService;
    private final ClaudeApiService claudeApiService;

    public RetagResult retagAll() {
        List<Long> ids = phraseRepository.findAll().stream()
                .map(Phrase::getId)
                .toList();
        log.info("[Retag] 시작 — 대상: {}개", ids.size());

        int updated = 0, errors = 0, skipped = 0;
        for (Long id : ids) {
            try {
                RetagOutcome outcome = retagOne(id);
                switch (outcome) {
                    case UPDATED -> updated++;
                    case SKIPPED -> skipped++;
                }
                Thread.sleep(1_000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("[Retag] 인터럽트 — 중단");
                break;
            } catch (Exception e) {
                errors++;
                log.error("[Retag] 실패 phrase {}: {}", id, e.getMessage());
            }
        }

        log.info("[Retag] 완료 — 갱신: {}, 스킵: {}, 실패: {}", updated, skipped, errors);
        return new RetagResult(ids.size(), updated, skipped, errors);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RetagOutcome retagOne(Long phraseId) {
        Phrase phrase = phraseRepository.findById(phraseId)
                .orElseThrow(() -> new IllegalArgumentException("Phrase 없음: " + phraseId));

        Book book = phrase.getBook();
        String category = fetchCategorySafely(book.getIsbn());

        List<String> newTagNames = claudeApiService.retagPhrase(
                book.getTitle(), book.getAuthor(), category, phrase.getText());

        if (newTagNames == null || newTagNames.isEmpty()) {
            log.warn("[Retag] 태그 0개 반환 — 스킵 [{}]", book.getTitle());
            return RetagOutcome.SKIPPED;
        }

        List<Tag> tags = tagRepository.findByNameIn(newTagNames);
        if (tags.isEmpty()) {
            log.warn("[Retag] 매칭되는 태그 없음 — 스킵 [{}] (응답: {})", book.getTitle(), newTagNames);
            return RetagOutcome.SKIPPED;
        }

        phrase.clearTags();
        tags.forEach(phrase::addTag);
        return RetagOutcome.UPDATED;
    }

    private String fetchCategorySafely(String isbn) {
        if (isbn == null || isbn.isBlank()) return null;
        try {
            AladinApiService.AladinBookInfo info = aladinApiService.fetchByIsbn(isbn);
            return info.categoryName();
        } catch (Exception e) {
            log.warn("[Retag] Aladin 카테고리 조회 실패 (ISBN {}): {}", isbn, e.getMessage());
            return null;
        }
    }

    public enum RetagOutcome { UPDATED, SKIPPED }

    public record RetagResult(int total, int updated, int skipped, int errors) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "total", total,
                    "updated", updated,
                    "skipped", skipped,
                    "errors", errors
            );
        }
    }
}
