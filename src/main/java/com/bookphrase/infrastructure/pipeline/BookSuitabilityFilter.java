package com.bookphrase.infrastructure.pipeline;

import com.bookphrase.infrastructure.aladin.AladinApiService.AladinBookInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 책이 O:GU(감성 독서 큐레이션)에 적합한지 1차 필터링합니다.
 *
 * [필터 1] 제목 키워드 블랙리스트 — 수험서/문제집 등 명백한 부적합
 * [필터 2] 카테고리 경로 블랙리스트 — 알라딘 카테고리에 부적합 키워드 포함
 * [필터 3] 오래됨 + 인기 없음 복합 필터 — 5년 이상 된 책 중 판매지수 낮으면 제외
 *          (스테디셀러는 판매지수가 높으므로 통과)
 *
 * 이 필터는 Claude API 호출 전에 실행되어 불필요한 API 비용을 줄입니다.
 */
@Slf4j
@Component
public class BookSuitabilityFilter {

    private static final int OLD_BOOK_YEARS = 5;
    private static final int MIN_SALES_POINT_FOR_OLD_BOOK = 200;

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
            // 요리/실용 무관
            "레시피", "요리책", "다이어트 식단",
            // 기타 부적합
            "문제 풀이", "풀이집", "해설집", "정답"
    );

    private static final List<String> CATEGORY_BLACKLIST = List.of(
            "수험서", "자격증", "유아", "어린이", "학습만화",
            "요리", "건강", "가정/육아", "컴퓨터/IT", "과학/기술",
            "대학교재", "취업/시험", "여행", "지도"
    );

    /**
     * 책의 O:GU 적합성을 종합 판단합니다.
     *
     * @return true = 적합 (계속 진행), false = 부적합 (스킵)
     */
    public boolean isSuitable(AladinBookInfo bookInfo) {
        String title = bookInfo.title();
        if (title == null || title.isBlank()) return false;

        // 1. 제목 키워드 필터
        String lowerTitle = title.toLowerCase();
        for (String keyword : TITLE_BLACKLIST) {
            if (lowerTitle.contains(keyword.toLowerCase())) {
                log.info("[BookFilter] 제목 키워드 → 스킵. [{}], 키워드: [{}]", title, keyword);
                return false;
            }
        }

        // 2. 카테고리 경로 필터
        String category = bookInfo.categoryName();
        if (category != null) {
            String lowerCategory = category.toLowerCase();
            for (String keyword : CATEGORY_BLACKLIST) {
                if (lowerCategory.contains(keyword.toLowerCase())) {
                    log.info("[BookFilter] 카테고리 → 스킵. [{}], 카테고리: [{}], 키워드: [{}]",
                            title, category, keyword);
                    return false;
                }
            }
        }

        // 3. 오래됨 + 인기 없음 복합 필터
        if (isOldAndUnpopular(bookInfo)) {
            log.info("[BookFilter] 오래됨+비인기 → 스킵. [{}], 출판: {}, 판매지수: {}",
                    title, bookInfo.pubDate(), bookInfo.salesPoint());
            return false;
        }

        return true;
    }

    private boolean isOldAndUnpopular(AladinBookInfo bookInfo) {
        String pubDate = bookInfo.pubDate();
        if (pubDate == null || pubDate.isBlank()) return false;

        try {
            LocalDate published = LocalDate.parse(pubDate, DateTimeFormatter.ISO_LOCAL_DATE);
            boolean isOld = published.isBefore(LocalDate.now().minusYears(OLD_BOOK_YEARS));

            if (isOld && bookInfo.salesPoint() < MIN_SALES_POINT_FOR_OLD_BOOK) {
                return true;
            }
        } catch (Exception e) {
            // 날짜 파싱 실패 시 필터 적용 안 함
        }
        return false;
    }
}
