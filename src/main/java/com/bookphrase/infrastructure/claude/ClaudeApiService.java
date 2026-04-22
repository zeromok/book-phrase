package com.bookphrase.infrastructure.claude;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Anthropic Claude API를 호출해 두 가지를 동시에 처리합니다.
 *
 * 1. 적합성 판단: 수험서·문제집·유아그림책 등 서비스 성격과 맞지 않는 책 필터링
 * 2. 문구 + 태그 생성: 적합한 책에 한해 감성 문구 1개 + 어울리는 태그 2개 생성
 *
 * 응답 형식:
 *   적합 → {"suitable": true,  "phrase": "...", "tags": ["...", "..."]}
 *   부적합 → {"suitable": false, "reason": "수험서라 서비스 성격과 맞지 않음"}
 *
 * [재시도 정책]
 *   429 Rate Limit 응답 시 최대 3회 지수 백오프 재시도 (2s → 5s → 10s)
 *   그 외 오류는 즉시 RuntimeException으로 상위에 전파
 */
@Slf4j
@Service
public class ClaudeApiService {

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String MODEL = "claude-haiku-4-5-20251001";

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {2_000L, 5_000L, 10_000L};

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
     * 책 정보를 받아 적합성 판단 + 감성 문구 + 태그를 한 번의 Claude 호출로 처리합니다.
     * 429 Rate Limit 발생 시 최대 {@value MAX_RETRIES}회 재시도합니다.
     *
     * @param title         책 제목
     * @param author        저자
     * @param categoryName  알라딘 카테고리 경로 (예: "국내도서>자기계발>성공/처세술")
     * @param availableTags DB에 존재하는 태그 이름 목록
     * @return ClaudeResult (suitable=false면 phrase/tags는 null)
     */
    public ClaudeResult evaluateAndGenerate(
            String title, String author, String categoryName,
            String pubDate, int salesPoint, List<String> availableTags) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY가 설정되지 않았습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", ANTHROPIC_VERSION);

        String prompt = buildPrompt(title, author, categoryName, pubDate, salesPoint, availableTags);

        Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "max_tokens", 300,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(CLAUDE_API_URL, request, String.class);

                String responseBody = response.getBody();
                log.debug("[ClaudeApiService] 원본 응답: {}", responseBody);

                // content[0].text 추출
                JsonNode root = objectMapper.readTree(responseBody);
                String text = root.path("content").get(0).path("text").asText().trim();
                log.info("[ClaudeApiService] Claude 텍스트: {}", text);

                // JSON 파싱 (Claude가 ```json ... ``` 코드블록으로 감쌀 수 있어 방어 처리)
                String json = extractJson(text);
                ClaudeResult result = objectMapper.readValue(json, ClaudeResult.class);

                if (result.suitable()) {
                    log.info("[ClaudeApiService] ✅ 적합 - 문구: [{}], 태그: {}", result.phrase(), result.tags());
                } else {
                    log.info("[ClaudeApiService] ❌ 부적합 - 사유: {}", result.reason());
                }

                return result;

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt < MAX_RETRIES - 1) {
                    long delay = RETRY_DELAYS_MS[attempt];
                    log.warn("[ClaudeApiService] ⚠️ 429 Rate Limit - {}ms 후 재시도 ({}/{}) [{}]",
                            delay, attempt + 1, MAX_RETRIES - 1, title);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Claude API 재시도 중 인터럽트 발생", ie);
                    }
                } else {
                    log.error("[ClaudeApiService] HTTP 오류 {}: {}", e.getStatusCode().value(), e.getMessage());
                    throw new RuntimeException("Claude API 호출 실패 (HTTP " + e.getStatusCode().value() + "): " + e.getMessage());
                }

            } catch (Exception e) {
                log.error("[ClaudeApiService] API 호출 실패: {}", e.getMessage(), e);
                throw new RuntimeException("Claude API 호출 실패: " + e.getMessage());
            }
        }

        // MAX_RETRIES 모두 소진 (429만 여기 도달 가능)
        log.error("[ClaudeApiService] 429 Rate Limit - 최대 재시도({}) 초과 [{}]", MAX_RETRIES, title);
        throw new RuntimeException("Claude API 429 Rate Limit - 최대 재시도 초과");
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

    private String buildPrompt(
            String title, String author, String categoryName,
            String pubDate, int salesPoint, List<String> availableTags) {

        String category = (categoryName != null && !categoryName.isBlank())
                ? categoryName : "알 수 없음";
        String pubInfo = (pubDate != null && !pubDate.isBlank())
                ? pubDate : "알 수 없음";

        return String.format("""
                당신은 "O:GU(오늘의 구절)" 서비스의 콘텐츠 편집자입니다.
                O:GU는 20~30대가 주 타겟인 감성 도서 큐레이션 서비스로, \
                짧은 문구 한 줄로 책에 대한 호기심을 자극하고, \
                독자의 현재 감정·상황과 공명하는 책을 발견하게 합니다.

                아래 책이 O:GU에 적합한지 판단하고, \
                적합하다면 감성적인 한국어 문구와 태그를 생성해주세요.

                ─ 책 정보 ─
                제목: %s
                저자: %s
                카테고리: %s
                출판일: %s
                판매지수: %d

                ─ O:GU 감성에 맞는 책 ─
                • 에세이, 소설, 시, 인문학, 철학, 심리학, 자기계발
                • 읽고 나면 여운이 남거나, 삶을 돌아보게 하는 책
                • 20~30대가 공감할 수 있는 주제: 관계, 성장, 불안, 위로, 일상, 사랑
                • 스테디셀러(오래되었지만 지금도 읽히는 명작)는 적합

                ─ O:GU 감성에 맞지 않는 책 ─
                • 수험서, 문제집, 자격증 교재, 학습서
                • 유아/어린이 도서, 학습만화
                • 요리 레시피, 여행 가이드, 지도, 실용서
                • 전문 기술서 (프로그래밍, 의학, 법률, 회계)
                • 특정 직업군/업계 대상 실무서
                • 시의성이 지난 트렌드서 (예: "2020년 경제 전망")
                • 감성적 공감보다 정보 전달이 목적인 책

                ─ 사용 가능한 태그 ─
                %s

                반드시 JSON 형식으로만 응답하세요 (다른 텍스트 없이):

                적합한 경우:
                {"suitable": true, "phrase": "20~50자의 감성적인 한국어 문구", "tags": ["태그1", "태그2"]}

                부적합한 경우:
                {"suitable": false, "reason": "부적합 사유를 한 문장으로"}
                """,
                title, author, category, pubInfo, salesPoint,
                String.join(", ", availableTags));
    }

    /**
     * Claude API 응답 결과
     *
     * suitable=true  → phrase, tags 에 값이 있음
     * suitable=false → reason 에 부적합 사유, phrase/tags 는 null
     */
    public record ClaudeResult(
            boolean suitable,
            String reason,
            String phrase,
            List<String> tags
    ) {
        public boolean isRejected() {
            return !suitable;
        }
    }
}
