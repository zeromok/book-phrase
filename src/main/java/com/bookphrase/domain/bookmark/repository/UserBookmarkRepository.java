package com.bookphrase.domain.bookmark.repository;

import com.bookphrase.domain.bookmark.entity.UserBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBookmarkRepository extends JpaRepository<UserBookmark, Long> {

    List<UserBookmark> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserBookmark> findByUserIdAndPhraseId(Long userId, Long phraseId);

    boolean existsByUserIdAndPhraseId(Long userId, Long phraseId);

    void deleteByUserIdAndPhraseId(Long userId, Long phraseId);
}
