package com.bookphrase.infrastructure.pipeline;

import com.bookphrase.infrastructure.aladin.AladinApiService.QueryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 매�� 오전 6시 (KST) 자동으로 AI 콘텐츠 파이프라인을 실행합니다.
 *
 * 요일별 로테이션으로 다양한 카테고리/소스를 순회하여
 * 베스트셀러 풀 고갈로 인한 저장 0개 문제를 해결합니다.
 *
 * 월=소설, 화=에세이, 수=자기계발, 목=인문학, 금=철학/종교, 토=역사, 일=전체베스트
 * 각 요일마다 BESTSELLER + BLOG_BEST 두 소스를 순회합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentPipelineScheduler {

    private static final int MAX_BOOKS_PER_SOURCE = 20;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ContentPipelineService contentPipelineService;

    /** 요일별 수집 대상: categoryId + queryType 조합 */
    private record DailyTarget(String label, Integer categoryId, List<QueryType> queryTypes) {}

    private DailyTarget getTarget(DayOfWeek day) {
        return switch (day) {
            case MONDAY    -> new DailyTarget("소설",     1,     List.of(QueryType.BESTSELLER, QueryType.BLOG_BEST));
            case TUESDAY   -> new DailyTarget("에세이",   55889, List.of(QueryType.BESTSELLER, QueryType.BLOG_BEST));
            case WEDNESDAY -> new DailyTarget("자기계발", 336,   List.of(QueryType.BESTSELLER, QueryType.NEW_SPECIAL));
            case THURSDAY  -> new DailyTarget("인문학",   656,   List.of(QueryType.BESTSELLER, QueryType.BLOG_BEST));
            case FRIDAY    -> new DailyTarget("철학/종교", 51,   List.of(QueryType.BESTSELLER, QueryType.NEW_SPECIAL));
            case SATURDAY  -> new DailyTarget("역사",     74,    List.of(QueryType.BESTSELLER, QueryType.BLOG_BEST));
            case SUNDAY    -> new DailyTarget("전체",     null,  List.of(QueryType.BESTSELLER, QueryType.NEW_SPECIAL, QueryType.BLOG_BEST));
        };
    }

    @Scheduled(cron = "0 0 21 * * *") // 매일 UTC 21:00 = KST 06:00
    public void runDailyPipeline() {
        DayOfWeek today = LocalDate.now(KST).getDayOfWeek();
        DailyTarget target = getTarget(today);

        log.info("[Scheduler] ===== 일일 파이프라인 시작 - {} ({}) =====", today, target.label());

        int totalSaved = 0, totalDuplicate = 0, totalKeyword = 0, totalClaude = 0, totalError = 0;

        for (QueryType queryType : target.queryTypes()) {
            try {
                log.info("[Scheduler] {} / {} 수집 시작", target.label(), queryType);
                ContentPipelineService.PipelineResult result =
                        contentPipelineService.runByCategory(queryType, target.categoryId(), MAX_BOOKS_PER_SOURCE);

                totalSaved     += result.saved();
                totalDuplicate += result.duplicate();
                totalKeyword   += result.keywordFiltered();
                totalClaude    += result.claudeFiltered();
                totalError     += result.error();

                log.info("[Scheduler] {} / {} 완료 - 저장: {}개 | 중복: {}개",
                        target.label(), queryType, result.saved(), result.duplicate());
            } catch (Exception e) {
                log.error("[Scheduler] {} / {} 오류: {}", target.label(), queryType, e.getMessage(), e);
            }
        }

        log.info("[Scheduler] ===== 일일 요약 [{}] - 저장: {}개 | ��복: {}개 | 키워드필터: {}개 | Claude필터: {}개 | 예외: {}개 =====",
                target.label(), totalSaved, totalDuplicate, totalKeyword, totalClaude, totalError);
    }
}
