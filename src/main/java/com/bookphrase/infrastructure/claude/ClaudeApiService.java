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
     * @return ClaudeResult (suitable=false면 phrase/tags는 null)
     */
    public ClaudeResult evaluateAndGenerate(
            String title, String author, String categoryName,
            String pubDate, int salesPoint) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY가 설정되지 않았습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", ANTHROPIC_VERSION);

        String prompt = buildPrompt(title, author, categoryName, pubDate, salesPoint);

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
            String pubDate, int salesPoint) {

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

                ─ 사용 가능한 태그 (독자 감정 상태 기준) ─
                각 태그는 *그 감정 상태의 독자가 찾을 만한 책*에 부여하세요.
                책 내용에 단어가 등장한다고 해서 부여하면 안 됩니다.

                • 위로받고싶다 🤗 — 지치고 상처받은 마음에 공감받고 싶은 독자
                  ✓ 위로 에세이, 따뜻한 문학, 공감 심리
                  ✗ 동기부여·생산성 책 (위로가 아니라 채찍질)

                • 자극받고싶다 🔥 — 동기부여·도전·돌파가 필요한 독자
                  ✓ 자기계발, 성공 에세이, 도전기
                  ✗ 잔잔한 일상 에세이

                • 쉬고싶다 😴 — 번잡한 일상에서 벗어나 고요함·여유를 찾는 독자
                  ✓ 잔잔한 에세이, 자연/계절/여행, 시, 일상의 여유
                  ✗ 자기계발·생산성·효율·마케팅 책 (쉬라는 말이 나와도 톤이 다름)

                • 성장하고싶다 🌱 — 더 나은 자신으로 발전하고 싶은 독자
                  ✓ 자기성찰 에세이, 인문학, 가치관·태도에 대한 책
                  ✗ 단순 성공·재테크 책

                • 사랑하고싶다 💕 — 관계와 사랑에 대해 깊이 느끼고 싶은 독자
                  ✓ 연애 에세이, 로맨스 소설, 관계 심리
                  ✗ "자기 자신을 사랑하라"류의 자기계발

                • 용기내고싶다 💪 — 두려움을 이기고 한 걸음 내딛고 싶은 독자
                  ✓ 변화·도전 에세이, 회복·재출발 이야기
                  ✗ 단순 성공담

                • 몰입하고싶다 📖 — 깊이 빠져들 이야기가 필요한 독자
                  ✓ 흡입력 있는 소설, 장르문학, 서사가 강한 책
                  ✗ 정보 전달성 에세이

                • 생각하고싶다 💭 — 깊이 사유하고 통찰을 얻고 싶은 독자
                  ✓ 철학, 인문학, 사회비평, 사유적 에세이
                  ✗ 실용적 자기계발

                ─ 태그 부여 규칙 ─
                1. 책의 *전체 톤과 결*을 보고 1~2개만 선택. 억지로 2개 채우지 말 것.
                2. 책 카테고리(%s)를 우선 참고. 예: 자기계발 책에 "쉬고싶다"는 거의 ❌.
                3. 문구에 단어가 나온다고 그 태그를 다는 게 아니라, *그 감정의 독자가 이 책을 펴서 만족할까*를 기준으로.

                반드시 JSON 형식으로만 응답하세요 (다른 텍스트 없이):

                적합한 경우:
                {"suitable": true, "phrase": "20~50자의 감성적인 한국어 문구", "tags": ["태그1"] 또는 ["태그1","태그2"]}

                부적합한 경우:
                {"suitable": false, "reason": "부적합 사유를 한 문장으로"}
                """,
                title, author, category, pubInfo, salesPoint, category);
    }

    /**
     * 기존 phrase에 새 프롬프트로 태그만 다시 부여합니다 (재태깅 전용).
     * phrase 텍스트는 변경하지 않습니다.
     *
     * @return 부여된 태그 이름 1~2개
     */
    public List<String> retagPhrase(
            String title, String author, String categoryName, String phraseText) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY가 설정되지 않았습니다.");
        }

        String category = (categoryName != null && !categoryName.isBlank())
                ? categoryName : "알 수 없음";
        String prompt = buildRetagPrompt(title, author, category, phraseText);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", ANTHROPIC_VERSION);

        Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "max_tokens", 100,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(CLAUDE_API_URL, request, String.class);
                JsonNode root = objectMapper.readTree(response.getBody());
                String text = root.path("content").get(0).path("text").asText().trim();
                String json = extractJson(text);

                JsonNode parsed = objectMapper.readTree(json);
                JsonNode tagsNode = parsed.path("tags");
                if (!tagsNode.isArray()) {
                    throw new RuntimeException("재태깅 응답에 tags 배열이 없음: " + text);
                }

                java.util.List<String> tagNames = new java.util.ArrayList<>();
                tagsNode.forEach(n -> tagNames.add(n.asText()));
                log.info("[ClaudeApiService] 재태깅 - [{}] → {}", title, tagNames);
                return tagNames;

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt < MAX_RETRIES - 1) {
                    long delay = RETRY_DELAYS_MS[attempt];
                    log.warn("[ClaudeApiService] 재태깅 429 - {}ms 후 재시도 [{}]", delay, title);
                    try { Thread.sleep(delay); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("재태깅 중 인터럽트", ie);
                    }
                } else {
                    throw new RuntimeException("재태깅 HTTP 오류 " + e.getStatusCode().value(), e);
                }
            } catch (Exception e) {
                throw new RuntimeException("재태깅 실패: " + e.getMessage(), e);
            }
        }

        throw new RuntimeException("재태깅 429 최대 재시도 초과 [" + title + "]");
    }

    private String buildRetagPrompt(String title, String author, String category, String phraseText) {
        return String.format("""
                당신은 "O:GU(오늘의 구절)" 서비스의 콘텐츠 편집자입니다.
                아래 책에서 발췌한 문구에 가장 어울리는 감정 태그 1~2개를 골라주세요.

                ─ 책 정보 ─
                제목: %s
                저자: %s
                카테고리: %s

                ─ 문구 ─
                "%s"

                ─ 사용 가능한 태그 (독자 감정 상태 기준) ─
                각 태그는 *그 감정 상태의 독자가 찾을 만한 책*에 부여하세요.

                • 위로받고싶다 🤗 — 지치고 상처받은 마음에 공감받고 싶은 독자
                  ✓ 위로 에세이, 따뜻한 문학, 공감 심리   ✗ 동기부여·생산성 책
                • 자극받고싶다 🔥 — 동기부여·도전·돌파가 필요한 독자
                  ✓ 자기계발, 성공 에세이, 도전기   ✗ 잔잔한 일상 에세이
                • 쉬고싶다 😴 — 번잡한 일상에서 벗어나 고요함을 찾는 독자
                  ✓ 잔잔한 에세이, 자연/계절/여행, 시   ✗ 자기계발·생산성·마케팅 책
                • 성장하고싶다 🌱 — 더 나은 자신으로 발전하고 싶은 독자
                  ✓ 자기성찰, 인문학, 가치관·태도   ✗ 단순 성공·재테크 책
                • 사랑하고싶다 💕 — 관계와 사랑에 깊이 공감하고 싶은 독자
                  ✓ 연애 에세이, 로맨스 소설, 관계 심리   ✗ "자기 사랑" 자기계발
                • 용기내고싶다 💪 — 두려움을 이기고 한 걸음 내딛고 싶은 독자
                  ✓ 변화·도전 에세이, 회복·재출발   ✗ 단순 성공담
                • 몰입하고싶다 📖 — 깊이 빠져들 이야기가 필요한 독자
                  ✓ 흡입력 있는 소설, 장르문학, 서사   ✗ 정보 전달성 에세이
                • 생각하고싶다 💭 — 깊이 사유하고 통찰을 얻고 싶은 독자
                  ✓ 철학, 인문학, 사회비평, 사유적 에세이   ✗ 실용적 자기계발

                ─ 부여 규칙 ─
                1. 책의 *전체 톤과 결*을 보고 1~2개만. 억지로 2개 채우지 말 것.
                2. 책 카테고리(%s) 우선. 자기계발 책에 "쉬고싶다"는 거의 ❌.
                3. 단어 등장 ≠ 태그 부여. *그 감정의 독자가 만족할까*가 기준.

                ⚠️ 태그 이름은 이모지·공백 빼고 *정확한 한글*만 반환하세요.
                예: "위로받고싶다 🤗" ❌, "위로받고싶다" ✅

                사용 가능한 태그 이름 (정확히 이 8개 중에서):
                위로받고싶다, 자극받고싶다, 쉬고싶다, 성장하고싶다, 사랑하고싶다, 용기내고싶다, 몰입하고싶다, 생각하고싶다

                JSON 형식으로만 응답:
                {"tags": ["위로받고싶다"]} 또는 {"tags": ["위로받고싶다","쉬고싶다"]}
                """,
                title, author, category, phraseText, category);
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
