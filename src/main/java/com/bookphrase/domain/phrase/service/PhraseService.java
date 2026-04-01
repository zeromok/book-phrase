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

    // 태그 기반 카드 피드 조회 (tagId 단수, userId nullable)
    public List<PhraseFeedResponse> getFeed(Long tagId, Long userId) {
        List<Phrase> phrases;

        if (tagId != null && userId != null) {
            // 특정 태그 + 히스토리 제외
            phrases = phraseRepository.findFeedByTagsExcludingHistory(List.of(tagId), userId, FEED_SIZE);
            if (phrases.size() < FEED_SIZE) {
                phrases = phraseRepository.findFeedByTagsAll(List.of(tagId), FEED_SIZE);
            }
        } else if (tagId != null) {
            // 특정 태그, 비로그인
            phrases = phraseRepository.findFeedByTagsAll(List.of(tagId), FEED_SIZE);
        } else if (userId != null) {
            // 전체 태그 + 히스토리 제외
            phrases = phraseRepository.findFeedExcludingHistory(userId, FEED_SIZE);
            if (phrases.size() < FEED_SIZE) {
                phrases = phraseRepository.findFeedAll(FEED_SIZE);
            }
        } else {
            // 전체 태그, 비로그인
            phrases = phraseRepository.findFeedAll(FEED_SIZE);
        }

        return phrases.stream()
                .map(p -> PhraseFeedResponse.of(
                        p,
                        userId != null && userBookmarkRepository.existsByUserIdAndPhraseId(userId, p.getId())
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
        User user = userRepository.getReferenceById(userId);
        Phrase phrase = phraseRepository.getReferenceById(phraseId);
        userHistoryRepository.save(UserHistory.builder()
                .user(user)
                .phrase(phrase)
                .build());
    }
}
