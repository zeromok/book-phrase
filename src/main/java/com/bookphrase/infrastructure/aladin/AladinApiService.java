package com.bookphrase.infrastructure.aladin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
public class AladinApiService {

    private static final String LOOKUP_URL = "https://www.aladin.co.kr/ttb/api/ItemLookUp.aspx";
    private static final String SEARCH_URL = "https://www.aladin.co.kr/ttb/api/ItemSearch.aspx";

    @Value("${aladin.ttb.key}")
    private String ttbKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AladinApiService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ── ISBN 단건 조회 (Admin 수동 등록용) ──────────────────────────────────
    public AladinBookInfo fetchByIsbn(String isbn) {
        String url = LOOKUP_URL
                + "?ttbkey=" + ttbKey
                + "&itemIdType=ISBN13"
                + "&ItemId=" + isbn
                + "&output=js"
                + "&Version=20131101"
                + "&Cover=Big";

        log.info("[AladinApiService] ISBN 조회 요청: {}", isbn);

        try {
            ResponseEntity<String> rawResponse = restTemplate.getForEntity(url, String.class);
            String body = rawResponse.getBody();
            AladinResponse response = objectMapper.readValue(body, AladinResponse.class);

            if (response.item() == null || response.item().isEmpty()) {
                throw new IllegalArgumentException("ISBN에 해당하는 책을 찾을 수 없습니다: " + isbn);
            }

            AladinBookInfo book = response.item().get(0);
            log.info("[AladinApiService] 조회 성공 - 제목: {}, 저자: {}", book.title(), book.author());
            return book;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AladinApiService] API 호출 실패: {}", e.getMessage(), e);
            throw new IllegalArgumentException("알라딘 API 호출 실패: " + e.getMessage());
        }
    }

    // ── 전체 베스트셀러 조회 (스케줄러 기본 실행용) ──────────────────────
    public List<AladinBookInfo> fetchBestsellers(int maxResults) {
        return fetchByCategory(QueryType.BESTSELLER, null, maxResults);
    }

    // ── 카테고리/QueryType 지정 조회 (초기 데이터 수집 + 다양한 소스용) ────
    /**
     * 알라딘 API에서 지정 조건으로 책 목록을 조회합니다.
     *
     * @param queryType  조회 유형 (BESTSELLER, NEW_SPECIAL, BLOG_BEST)
     * @param categoryId 알라딘 카테고리 ID (null이면 전체 카테고리)
     *                   주요 ID:
     *                     1     = 소설/시/희곡
     *                     55889 = 에세이
     *                     336   = 자기계발
     *                     656   = 인문학
     *                     51    = 철학/종교
     *                     74    = 역사
     *                     798   = 사회과학
     * @param maxResults 최대 결과 수 (1~50)
     */
    public List<AladinBookInfo> fetchByCategory(QueryType queryType, Integer categoryId, int maxResults) {
        StringBuilder url = new StringBuilder(SEARCH_URL)
                .append("?ttbkey=").append(ttbKey)
                .append("&Query=").append(queryType.queryParam)
                .append("&QueryType=").append(queryType.queryType)
                .append("&MaxResults=").append(Math.min(maxResults, 50))
                .append("&start=1")
                .append("&SearchTarget=Book")
                .append("&output=js")
                .append("&Version=20131101")
                .append("&Cover=Big");

        if (categoryId != null) {
            url.append("&CategoryId=").append(categoryId);
        }

        log.info("[AladinApiService] 카테고리 조회 - queryType={}, categoryId={}, maxResults={}",
                queryType, categoryId, maxResults);

        try {
            ResponseEntity<String> rawResponse = restTemplate.getForEntity(url.toString(), String.class);
            String body = rawResponse.getBody();
            AladinResponse response = objectMapper.readValue(body, AladinResponse.class);

            if (response.item() == null) {
                log.warn("[AladinApiService] 조회 결과 없음");
                return List.of();
            }

            log.info("[AladinApiService] {}권 조회 완료", response.item().size());
            return response.item();

        } catch (Exception e) {
            log.error("[AladinApiService] 카테고리 조회 실패: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // ── 조회 유형 ─────────────────────────────────────────────────────────
    /**
     * 알라딘 ItemSearch API의 QueryType 파라미터
     *
     * BESTSELLER   : 종합 베스트셀러 (가장 많이 팔린 책)
     * NEW_SPECIAL  : 화제의 신간 (최신 + 화제성)
     * BLOG_BEST    : 블로거 베스트 (블로그 리뷰 기반, 감성 도서 비중 높음)
     */
    public enum QueryType {
        BESTSELLER("bestseller", "Bestseller"),
        NEW_SPECIAL("ItemNewSpecial", "ItemNewSpecial"),
        BLOG_BEST("BlogBest", "BlogBest");

        public final String queryParam;
        public final String queryType;

        QueryType(String queryParam, String queryType) {
            this.queryParam = queryParam;
            this.queryType  = queryType;
        }
    }

    // ── DTO ──────────────────────────────────────────────────────────────
    public record AladinResponse(List<AladinBookInfo> item) {}

    /**
     * 알라딘 API 응답 DTO
     *
     * categoryName 예시:
     *   "국내도서>자기계발>성공/처세술"
     *   "국내도서>소설/시/희곡>한국소설"
     *   "국내도서>수험서/자격증>공무원 수험서"
     */
    public record AladinBookInfo(
            String title,
            String author,
            String publisher,
            String isbn13,
            String cover,
            String link,
            String categoryName
    ) {}
}
