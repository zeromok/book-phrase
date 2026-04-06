package com.bookphrase.domain.phrase.service;

import com.bookphrase.api.phrase.dto.PhraseFeedResponse;
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

    private static final int FEED_SIZE = 10;

    private final PhraseRepository phraseRepository;

    // 태그 기반 카드 피드 조회 (로그인 없음, 히스토리 없음)
    public List<PhraseFeedResponse> getFeed(Long tagId) {
        List<Phrase> phrases;

        if (tagId != null) {
            phrases = phraseRepository.findFeedByTagsAll(List.of(tagId), FEED_SIZE);
        } else {
            phrases = phraseRepository.findFeedAll(FEED_SIZE);
        }

        return phrases.stream()
                .map(p -> new PhraseFeedResponse(p.getId(), p.getText()))
                .toList();
    }

    // 카드 탭 → 책 정보 공개 (reveal)
    public PhraseRevealResponse reveal(Long phraseId) {
        Phrase phrase = phraseRepository.findById(phraseId)
                .orElseThrow(() -> new IllegalArgumentException("문구를 찾을 수 없습니다."));
        return PhraseRevealResponse.from(phrase);
    }
}
