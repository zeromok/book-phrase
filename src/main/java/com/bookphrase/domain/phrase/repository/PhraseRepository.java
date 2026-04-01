package com.bookphrase.domain.phrase.repository;

import com.bookphrase.domain.phrase.entity.Phrase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PhraseRepository extends JpaRepository<Phrase, Long> {

    // 특정 태그 + 히스토리 제외
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

    // 특정 태그 + 히스토리 무시 (fallback)
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

    // 전체 태그 + 히스토리 제외
    @Query(value = """
            SELECT p.* FROM phrases p
            WHERE p.id NOT IN (
                SELECT phrase_id FROM user_histories WHERE user_id = :userId
            )
            ORDER BY RAND()
            LIMIT :limit
            """, nativeQuery = true)
    List<Phrase> findFeedExcludingHistory(
            @Param("userId") Long userId,
            @Param("limit") int limit
    );

    // 전체 태그 + 히스토리 무시 (fallback / 비로그인)
    @Query(value = "SELECT p.* FROM phrases p ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<Phrase> findFeedAll(@Param("limit") int limit);
}
