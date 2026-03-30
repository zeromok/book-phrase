package com.bookphrase.domain.phrase.repository;

import com.bookphrase.domain.phrase.entity.Phrase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PhraseRepository extends JpaRepository<Phrase, Long> {

    // 태그 기반 피드 조회: 이미 본 것 제외, 랜덤 10개
    @Query(value = """
            SELECT DISTINCT p.* FROM phrases p
            JOIN phrase_tags pt ON p.id = pt.phrase_id
            WHERE pt.tag_id IN (:tagIds)
            AND p.id NOT IN (
                SELECT phrase_id FROM user_histories WHERE user_id = :userId
            )
            ORDER BY RAND()
            LIMIT :limit
            """, nativeQuery = true)
    List<Phrase> findFeedByTagsExcludingHistory(
            @Param("tagIds") List<Long> tagIds,
            @Param("userId") Long userId,
            @Param("limit") int limit
    );

    // 히스토리 부족 시 fallback: 오래된 것부터 다시 포함
    @Query(value = """
            SELECT DISTINCT p.* FROM phrases p
            JOIN phrase_tags pt ON p.id = pt.phrase_id
            WHERE pt.tag_id IN (:tagIds)
            ORDER BY RAND()
            LIMIT :limit
            """, nativeQuery = true)
    List<Phrase> findFeedByTagsAll(
            @Param("tagIds") List<Long> tagIds,
            @Param("limit") int limit
    );
}
