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

    private static final String BASE_URL = "https://www.aladin.co.kr/ttb/api/ItemLookUp.aspx";

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

    public AladinBookInfo fetchByIsbn(String isbn) {
        String url = BASE_URL
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
            log.info("[AladinApiService] 응답 body: {}", body);

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

    public record AladinResponse(List<AladinBookInfo> item) {}

    public record AladinBookInfo(
            String title,
            String author,
            String publisher,
            String isbn13,
            String cover,
            String link
    ) {}
}
