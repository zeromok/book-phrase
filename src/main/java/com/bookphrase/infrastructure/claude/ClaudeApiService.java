package com.bookphrase.infrastructure.claude;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Anthropic Claude API를 호출해 책 제목+저자 → 감성 문구 + 태그 2개를 생성합니다.
 *
 * 응답 형식 (Claude에게 강제):
 * {"phrase": "감성적인 한국어 문구", "tags": ["태그1", "태그2"]}
 */
@Slf4j
@Service
public class ClaudeApiService {

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String MODEL = "claude-haiku-4-5-20251001";

    @Value("${anthropic.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ClaudeApiService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 책 정보를 받아 감성 문구 1개 + 어울리는 태그 2개를 생성합니다.
     *
     * @param title         책 제목
     * @param author        저자
     * @param availableTags DB에 존재하는 태그 이름 목록
     * @return ClaudeResult(phrase, tags)
     */
    public ClaudeResult generatePhraseAndTags(String title, String author, List<String> availableTags) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY가 설정되지 않았습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", ANTHROPIC_VERSION);

        String prompt = buildPrompt(title, author, availableTags);

        Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "max_tokens", 300,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(CLAUDE_API_URL, request, String.class);

            String responseBody = response.getBody();
            log.debug("[ClaudeApiService] 원본 응답: {}", responseBody);

            // content[0].text 추출
            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("content").get(0).path("text").asText().trim();
            log.info("[ClaudeApiService] Claude 텍스트: {}", text);

            // JSON 파싱 (Claude가 가끔 ```json ... ``` 코드블록으로 감쌀 수 있음)
            String json = extractJson(text);
            ClaudeResult result = objectMapper.readValue(json, ClaudeResult.class);
            log.info("[ClaudeApiService] 생성 완료 - 문구: [{}], 태그: {}", result.phrase(), result.tags());
            return result;

        } catch (Exception e) {
            log.error("[ClaudeApiService] API 호출 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Claude API 호출 실패: " + e.getMessage());
        }
    }

    /**
     * Claude 응답에서 JSON 부분만 추출합니다.
     * Claude가 ```json ... ``` 코드블록으로 감쌀 수 있어 방어 처리합니다.
     */
    private String extractJson(String text) {
        if (text.contains("{")) {
            int start = text.indexOf('{');
            int end   = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return text.substring(start, end + 1);
            }
        }
        return text;
    }

    private String buildPrompt(String title, String author, List<String> availableTags) {
        return String.format("""
                아래 책을 읽고 싶게 만드는 감성적인 한국어 문구 1개와,
                제공된 태그 중 이 책과 가장 어울리는 것 2개를 선택해주세요.
                
                책 제목: %s
                저자: %s
                
                사용 가능한 태그: %s
                
                규칙:
                - 문구는 20~50자 사이의 짧고 감성적인 한국어 문장
                - 태그는 반드시 위의 목록에서만 선택
                - 반드시 JSON 형식으로만 응답 (다른 텍스트 없이)
                
                응답 형식:
                {"phrase": "감성적인 문구", "tags": ["태그1", "태그2"]}
                """,
                title, author, String.join(", ", availableTags));
    }

    public record ClaudeResult(String phrase, List<String> tags) {}
}
