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
    private static final String SEARCH_URL  = "https://www.aladin.co.kr/ttb/api/ItemSearch.aspx";

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
            log.info("[AladinApiService] 응답 코드: {}, Content-Type: {}",
                    rawResponse.getStatusCode(), rawResponse.getHeaders().getContentType());

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

    // ── 베스트셀러 목록 조회 (AI 파이프라인용) ────────────────────────────
    public List<AladinBookInfo> fetchBestsellers(int maxResults) {
        String url = SEARCH_URL
                + "?ttbkey=" + ttbKey
                + "&Query=bestseller"
                + "&QueryType=Bestseller"
                + "&MaxResults=" + maxResults
                + "&start=1"
                + "&SearchTarget=Book"
                + "&output=js"
                + "&Version=20131101"
                + "&Cover=Big";

        log.info("[AladinApiService] 베스트셀러 조회 요청 (maxResults={})", maxResults);

        try {
            ResponseEntity<String> rawResponse = restTemplate.getForEntity(url, String.class);
            String body = rawResponse.getBody();
            log.debug("[AladinApiService] 베스트셀러 응답: {}", body);

            AladinResponse response = objectMapper.readValue(body, AladinResponse.class);

            if (response.item() == null) {
                log.warn("[AladinApiService] 베스트셀러 결과 없음");
                return List.of();
            }

            log.info("[AladinApiService] 베스트셀러 {}권 조회 완료", response.item().size());
            return response.item();

        } catch (Exception e) {
            log.error("[AladinApiService] 베스트셀러 조회 실패: {}", e.getMessage(), e);
            return List.of();
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
     *   "국내도서>유아>그림책"
     */
    public record AladinBookInfo(
            String title,
            String author,
            String publisher,
            String isbn13,
            String cover,
            String link,
            String categoryName  // 카테고리 경로 (적합성 판단에 활용)
    ) {}
}
