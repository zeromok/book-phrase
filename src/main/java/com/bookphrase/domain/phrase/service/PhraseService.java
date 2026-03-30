package com.bookphrase.domain.phrase.service;

import com.bookphrase.api.phrase.dto.PhraseFeedResponse;
import com.bookphrase.api.phrase.dto.PhraseRevealResponse;
import com.bookphrase.domain.bookmark.repository.UserBookmarkRepository;
import com.bookphrase.domain.phrase.entity.Phrase;
import com.bookphrase.domain.phrase.entity.UserHistory;
import com.bookphrase.domain.phrase.repository.PhraseRepository;
import com.bookphrase.domain.phrase.repository.UserHistoryRepository;
import com.bookphrase.domain.user.entity.User;
import com.bookphrase.domain.user.repository.UserRepository;
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
    private final UserHistoryRepository userHistoryRepository;
    private final UserBookmarkRepository userBookmarkRepository;
    private final UserRepository userRepository;

    // 태그 기반 카드 피드 조회
    public List<PhraseFeedResponse> getFeed(List<Long> tagIds, Long userId) {
        List<Phrase> phrases = phraseRepository
                .findFeedByTagsExcludingHistory(tagIds, userId, FEED_SIZE);

        // 히스토리 제외 후 부족하면 전체에서 보충
        if (phrases.size() < FEED_SIZE) {
            phrases = phraseRepository.findFeedByTagsAll(tagIds, FEED_SIZE);
        }

        return phrases.stream()
                .map(p -> PhraseFeedResponse.of(
                        p,
                        userBookmarkRepository.existsByUserIdAndPhraseId(userId, p.getId())
                ))
                .toList();
    }

    // 카드 탭 → 책 정보 공개 (reveal)
    public PhraseRevealResponse reveal(Long phraseId) {
        Phrase phrase = phraseRepository.findById(phraseId)
                .orElseThrow(() -> new IllegalArgumentException("문구를 찾을 수 없습니다."));
        return PhraseRevealResponse.from(phrase);
    }

    // 카드 조회 기록 저장
    @Transactional
    public void recordView(Long phraseId, Long userId) {
        // 이미 본 카드도 히스토리에 계속 쌓음 (추후 열람 빈도 분석용)
        User user = userRepository.getReferenceById(userId);
        Phrase phrase = phraseRepository.getReferenceById(phraseId);
        userHistoryRepository.save(UserHistory.builder()
                .user(user)
                .phrase(phrase)
                .build());
    }
}
