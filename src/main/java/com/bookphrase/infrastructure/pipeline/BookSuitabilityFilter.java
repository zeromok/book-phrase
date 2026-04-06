package com.bookphrase.infrastructure.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 책이 BookPhrase(감성 독서 앱)에 적합한지 1차 필터링합니다.
 *
 * 기준:
 * - 부적합: 문제집, 수험서, 자격증 교재, 학습지, 어학 교재 등
 * - 적합:   소설, 에세이, 자기계발, 인문학, 시집, 철학서 등
 *
 * 이 필터는 Claude API 호출 전에 실행되어 불필요한 API 비용을 줄입니다.
 * 2차 필터는 ClaudeApiService의 suitable 필드로 처리합니다.
 */
@Slf4j
@Component
public class BookSuitabilityFilter {

    /**
     * 제목에서 부적합 책을 탐지하는 키워드 블랙리스트.
     * 대소문자 무관, 부분 일치로 검사합니다.
     */
    private static final List<String> TITLE_BLACKLIST = List.of(
            // 수험/시험 관련
            "수능", "기출", "모의고사", "수험", "입시", "합격",
            "문제집", "워크북", "workbook", "연습문제", "예상문제",
            // 어학 교재
            "토익", "toeic", "토플", "toefl", "jlpt", "opic",
            "영어 회화", "영어회화", "일본어", "중국어 회화",
            "HSK", "IELTS",
            // 자격증/전문시험
            "자격증", "공무원", "공인중개사", "한국사능력검정",
            "전기기사", "산업기사", "기사 시험",
            // 학습 교재
            "교과서", "학습지", "초등", "중학교", "고등학교",
            "개념원리", "rpm", "쎈 수학", "자이스토리",
            // 요리/실용 무관 (감성과 거리 먼 실용서)
            "레시피", "요리책", "다이어트 식단",
            // 기타 부적합
            "문제 풀이", "풀이집", "해설집", "정답"
    );

    /**
     * 책 제목을 기준으로 1차 적합성을 판단합니다.
     *
     * @param title 책 제목
     * @return true = 적합 (계속 진행), false = 부적합 (스킵)
     */
    public boolean isSuitable(String title) {
        if (title == null || title.isBlank()) return false;

        String lowerTitle = title.toLowerCase();

        for (String keyword : TITLE_BLACKLIST) {
            if (lowerTitle.contains(keyword.toLowerCase())) {
                log.info("[BookFilter] 부적합 키워드 감지 → 스킵. 제목: [{}], 키워드: [{}]",
                        title, keyword);
                return false;
            }
        }
        return true;
    }
}
