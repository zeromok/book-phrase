package com.bookphrase.domain.phrase.service;

import com.bookphrase.api.phrase.dto.PhraseFeedResponse;
import com.bookphrase.api.phrase.dto.PhraseFeedResponse.PhraseItem;
import com.bookphrase.api.phrase.dto.PhraseRevealResponse;
import com.bookphrase.domain.phrase.entity.Phrase;
import com.bookphrase.domain.phrase.repository.PhraseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhraseService {

    private static final int DEFAULT_SIZE = 10;

    private final PhraseRepository phraseRepository;

    public PhraseFeedResponse getFeed(Long tagId, long seed, int page, int size) {
        if (size <= 0 || size > 50) size = DEFAULT_SIZE;
        int offset = page * size;
        // 1개 더 조회하여 hasNext 판단
        int fetchSize = size + 1;

        List<Phrase> phrases;
        if (tagId != null) {
            phrases = phraseRepository.findFeedByTag(List.of(tagId), seed, offset, fetchSize);
        } else {
            phrases = phraseRepository.findFeedAll(seed, offset, fetchSize);
        }

        boolean hasNext = phrases.size() > size;
        if (hasNext) {
            phrases = phrases.subList(0, size);
        }

        List<PhraseItem> items = phrases.stream()
                .map(p -> new PhraseItem(p.getId(), p.getText()))
                .toList();

        return new PhraseFeedResponse(items, hasNext);
    }

    // 카드 탭 → 책 정보 공개 (reveal)
    public PhraseRevealResponse reveal(Long phraseId) {
        Phrase phrase = phraseRepository.findById(phraseId)
                .orElseThrow(() -> new IllegalArgumentException("문구를 찾을 수 없습니다."));
        return PhraseRevealResponse.from(phrase);
    }
}
