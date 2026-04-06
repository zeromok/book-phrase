package com.bookphrase.domain.phrase.repository;

import com.bookphrase.domain.phrase.entity.Phrase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PhraseRepository extends JpaRepository<Phrase, Long> {

    // 특정 태그 기반 피드 (랜덤)
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

    // 전체 태그 피드 (랜덤, 비로그인/태그선택 없을 때)
    @Query(value = "SELECT p.* FROM phrases p ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<Phrase> findFeedAll(@Param("limit") int limit);
}
