package com.bookphrase.domain.bookmark.service;

import com.bookphrase.api.bookmark.dto.BookmarkResponse;
import com.bookphrase.domain.bookmark.entity.UserBookmark;
import com.bookphrase.domain.bookmark.repository.UserBookmarkRepository;
import com.bookphrase.domain.phrase.entity.Phrase;
import com.bookphrase.domain.phrase.repository.PhraseRepository;
import com.bookphrase.domain.user.entity.User;
import com.bookphrase.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkService {

    private final UserBookmarkRepository userBookmarkRepository;
    private final UserRepository userRepository;
    private final PhraseRepository phraseRepository;

    // 찜 추가
    @Transactional
    public void addBookmark(Long userId, Long phraseId) {
        if (userBookmarkRepository.existsByUserIdAndPhraseId(userId, phraseId)) {
            return; // 이미 찜한 경우 무시 (멱등성)
        }
        User user = userRepository.getReferenceById(userId);
        Phrase phrase = phraseRepository.findById(phraseId)
                .orElseThrow(() -> new IllegalArgumentException("문구를 찾을 수 없습니다."));

        userBookmarkRepository.save(UserBookmark.builder()
                .user(user)
                .phrase(phrase)
                .build());
    }

    // 찜 해제
    @Transactional
    public void removeBookmark(Long userId, Long phraseId) {
        userBookmarkRepository.deleteByUserIdAndPhraseId(userId, phraseId);
    }

    // 내 찜 목록
    public List<BookmarkResponse> getBookmarks(Long userId) {
        return userBookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(BookmarkResponse::from)
                .toList();
    }
}
