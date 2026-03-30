package com.bookphrase.domain.phrase.repository;

import com.bookphrase.domain.phrase.entity.UserHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserHistoryRepository extends JpaRepository<UserHistory, Long> {

    List<UserHistory> findByUserIdOrderByViewedAtDesc(Long userId);

    boolean existsByUserIdAndPhraseId(Long userId, Long phraseId);
}
