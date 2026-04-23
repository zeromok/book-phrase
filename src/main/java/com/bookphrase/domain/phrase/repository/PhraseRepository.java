package com.bookphrase.domain.phrase.repository;

import com.bookphrase.domain.phrase.entity.Phrase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PhraseRepository extends JpaRepository<Phrase, Long> {

    // seed 기반 랜덤 정렬 + 태그 필터 + 페이지네이션
    @Query(value = """
            SELECT DISTINCT p.* FROM phrases p
            JOIN phrase_tags pt ON p.id = pt.phrase_id
            WHERE pt.tag_id IN (:tagIds)
            ORDER BY RAND(:seed)
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Phrase> findFeedByTag(
            @Param("tagIds") List<Long> tagIds,
            @Param("seed") long seed,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    // seed 기반 랜덤 정렬 + 전체 + 페이지네이션
    @Query(value = """
            SELECT p.* FROM phrases p
            ORDER BY RAND(:seed)
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Phrase> findFeedAll(
            @Param("seed") long seed,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    // 오늘의 구절: 날짜 seed 기반으로 매일 1개 선정 (모든 사용자 동일)
    @Query(value = "SELECT p.* FROM phrases p ORDER BY RAND(:dateSeed) LIMIT 1", nativeQuery = true)
    Phrase findDaily(@Param("dateSeed") long dateSeed);
}
