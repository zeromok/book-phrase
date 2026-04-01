package com.bookphrase.infrastructure.aladin;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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

    public AladinApiService() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(new ObjectMapper());
        converter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                MediaType.TEXT_PLAIN,
                new MediaType("text", "javascript"),
                new MediaType("text", "html")
        ));
        this.restTemplate = new RestTemplate(List.of(converter));
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
        AladinResponse response = restTemplate.getForObject(url, AladinResponse.class);

        if (response == null || response.item() == null || response.item().isEmpty()) {
            throw new IllegalArgumentException("ISBN에 해당하는 책을 찾을 수 없습니다: " + isbn);
        }

        return response.item().get(0);
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
