package com.bookphrase.infrastructure.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 오전 6시 (KST) 자동으로 AI 콘텐츠 파이프라인을 실행합니다.
 *
 * Railway 서버는 UTC 기준으로 동작하므로:
 * KST 06:00 = UTC 21:00 (전날)
 * cron: "0 0 21 * * *"
 *
 * @EnableScheduling 은 BookPhraseApplication 에 설정
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentPipelineScheduler {

    private static final int DEFAULT_MAX_BOOKS = 10;

    private final ContentPipelineService contentPipelineService;

    @Scheduled(cron = "0 0 21 * * *") // 매일 UTC 21:00 = KST 06:00
    public void runDailyPipeline() {
        log.info("[Scheduler] ===== 일일 AI 콘텐츠 파이프라인 시작 =====");
        try {
            int count = contentPipelineService.runPipeline(DEFAULT_MAX_BOOKS);
            log.info("[Scheduler] ===== 일일 파이프라인 완료. 신규 콘텐츠: {}개 =====", count);
        } catch (Exception e) {
            log.error("[Scheduler] 일일 파이프라인 실행 중 오류: {}", e.getMessage(), e);
        }
    }
}
