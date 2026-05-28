package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardBreakdownDimension;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardMetric;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository;
import de.tum.cit.aet.artemis.iris.service.IrisAdminDashboardService;

class IrisAdminDashboardServiceTest {

    private IrisAdminDashboardRepository repository;

    private IrisDashboardProperties properties;

    private CourseRepository courseRepository;

    private IrisAdminDashboardService service;

    @BeforeEach
    void setUp() {
        repository = mock(IrisAdminDashboardRepository.class);
        properties = new IrisDashboardProperties();
        courseRepository = mock(CourseRepository.class);
        service = new IrisAdminDashboardService(repository, properties, courseRepository);
        TimeUtil.setClock(Clock.fixed(ZonedDateTime.of(2026, 5, 27, 12, 0, 0, 0, ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
    }

    // -- Overview tests ---------------------------------------------------------

    @Test
    void computeNoResponseRate_emptyResults_returnsZero() {
        stubAllRepositoryMethods();
        var overview = service.computeOverview(Instant.parse("2026-05-26T00:00:00Z"), Instant.parse("2026-05-27T00:00:00Z"));
        assertThat(overview.noResponseRate()).isEqualTo(0.0);
        assertThat(overview.noResponseMessageCount()).isEqualTo(0);
    }

    @Test
    void computeNoResponseRate_withResponses_computesCorrectly() {
        stubAllRepositoryMethods();
        // Two user messages: one with LLM response, one without (both within staleBefore)
        when(repository.findUserMessagesWithNextMessageFullRange(any(), any()))
                .thenReturn(List.of(new Object[] { 1L, 100L, Instant.parse("2026-05-26T10:00:00Z"), "LLM", Instant.parse("2026-05-26T10:00:05Z"), "CHAT" },
                        new Object[] { 2L, 101L, Instant.parse("2026-05-26T11:00:00Z"), null, null, "CHAT" }));
        var overview = service.computeOverview(Instant.parse("2026-05-26T00:00:00Z"), Instant.parse("2026-05-27T00:00:00Z"));
        assertThat(overview.noResponseRate()).isEqualTo(50.0);
        assertThat(overview.noResponseMessageCount()).isEqualTo(1);
        assertThat(overview.noResponseSessionCount()).isEqualTo(1);
    }

    @Test
    void engagementRate_zeroTotalSessions_returnsZero() {
        stubAllRepositoryMethods();
        var overview = service.computeOverview(Instant.parse("2026-05-26T00:00:00Z"), Instant.parse("2026-05-27T00:00:00Z"));
        assertThat(overview.engagementRate()).isEqualTo(0.0);
    }

    @Test
    void engagementRate_withSessions_computesCorrectly() {
        stubAllRepositoryMethods();
        when(repository.countTotalSessions(any(), any())).thenReturn(10L);
        when(repository.countActiveSessions(any(), any())).thenReturn(7L);
        var overview = service.computeOverview(Instant.parse("2026-05-26T00:00:00Z"), Instant.parse("2026-05-27T00:00:00Z"));
        assertThat(overview.engagementRate()).isEqualTo(70.0);
    }

    @Test
    void overview_thumbsRatios_computedCorrectly() {
        stubAllRepositoryMethods();
        when(repository.countTotalSessions(any(), any())).thenReturn(100L);
        when(repository.countThumbsUp(any(), any())).thenReturn(20L);
        when(repository.countThumbsDown(any(), any())).thenReturn(5L);
        when(repository.countTotalLlmMessages(any(), any())).thenReturn(200L);
        when(repository.countSessionsWithThumbsUp(any(), any())).thenReturn(15L);
        when(repository.countSessionsWithThumbsDown(any(), any())).thenReturn(4L);
        var overview = service.computeOverview(Instant.parse("2026-05-26T00:00:00Z"), Instant.parse("2026-05-27T00:00:00Z"));
        assertThat(overview.thumbsUpRatio()).isEqualTo(10.0);
        assertThat(overview.thumbsDownRatio()).isEqualTo(2.5);
        assertThat(overview.thumbsUpAbsoluteRate()).isEqualTo(15.0);
        assertThat(overview.thumbsDownAbsoluteRate()).isEqualTo(4.0);
    }

    @Test
    void overview_responseTime_computedCorrectly() {
        stubAllRepositoryMethods();
        // Two messages with LLM responses: 5s and 10s response times
        when(repository.findUserMessagesWithNextMessageFullRange(any(), any()))
                .thenReturn(List.of(new Object[] { 1L, 100L, Instant.parse("2026-05-26T10:00:00Z"), "LLM", Instant.parse("2026-05-26T10:00:05Z"), "CHAT" },
                        new Object[] { 2L, 101L, Instant.parse("2026-05-26T11:00:00Z"), "LLM", Instant.parse("2026-05-26T11:00:10Z"), "CHAT" }));
        var overview = service.computeOverview(Instant.parse("2026-05-26T00:00:00Z"), Instant.parse("2026-05-27T00:00:00Z"));
        assertThat(overview.avgResponseTimeSeconds()).isEqualTo(7.5);
        assertThat(overview.p50ResponseTimeSeconds()).isEqualTo(7.5);
    }

    @Test
    void overview_tokenCost_summedCorrectly() {
        stubAllRepositoryMethods();
        when(repository.computeTokenCost(any(), any())).thenReturn(List.of(new Object[] { 1, 1.50 }, new Object[] { 0, 0.75 }));
        var overview = service.computeOverview(Instant.parse("2026-05-26T00:00:00Z"), Instant.parse("2026-05-27T00:00:00Z"));
        assertThat(overview.totalTokenCostEur()).isEqualTo(2.25);
    }

    // -- Percentile tests -------------------------------------------------------

    @Test
    void percentile_emptyList_returnsZero() {
        assertThat(IrisAdminDashboardService.percentile(List.of(), 0.5)).isEqualTo(0.0);
    }

    @Test
    void percentile_singleElement_returnsThatElement() {
        var durations = List.of(Duration.ofSeconds(5));
        assertThat(IrisAdminDashboardService.percentile(durations, 0.5)).isEqualTo(5.0);
        assertThat(IrisAdminDashboardService.percentile(durations, 0.95)).isEqualTo(5.0);
    }

    @Test
    void percentile_multipleElements_interpolatesCorrectly() {
        var durations = List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(4), Duration.ofSeconds(5));
        assertThat(IrisAdminDashboardService.percentile(durations, 0.5)).isEqualTo(3.0);
    }

    @Test
    void percentile_p95_interpolatesCorrectly() {
        var durations = List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofSeconds(4), Duration.ofSeconds(5));
        // p95 index = 0.95 * 4 = 3.8, interpolate between 4s and 5s: 4 + 0.8 * 1 = 4.8
        assertThat(IrisAdminDashboardService.percentile(durations, 0.95)).isEqualTo(4.8);
    }

    // -- Config test ------------------------------------------------------------

    @Test
    void getConfig_returnsProperties() {
        properties.setMaxQueryWindowDays(30);
        properties.setStaleThresholdMinutes(10);
        var config = service.getConfig();
        assertThat(config.maxQueryWindowDays()).isEqualTo(30);
        assertThat(config.staleThresholdMinutes()).isEqualTo(10);
    }

    // -- Stale-before tests -----------------------------------------------------

    @Test
    void computeStaleBefore_toBeforeStaleLimit_returnsTo() {
        Instant now = Instant.parse("2026-05-27T12:00:00Z");
        Instant to = Instant.parse("2026-05-27T11:50:00Z"); // 10 min before now, stale threshold is 5 min
        Instant result = service.computeStaleBefore(to, now);
        assertThat(result).isEqualTo(to);
    }

    @Test
    void computeStaleBefore_toAfterStaleLimit_returnsStaleLimit() {
        Instant now = Instant.parse("2026-05-27T12:00:00Z");
        Instant to = Instant.parse("2026-05-27T12:00:00Z"); // same as now
        Instant result = service.computeStaleBefore(to, now);
        // staleThresholdMinutes defaults to 5
        assertThat(result).isEqualTo(Instant.parse("2026-05-27T11:55:00Z"));
    }

    // -- Bucket generation tests ------------------------------------------------

    @Test
    void generateBuckets_dailySpan_correctBuckets() {
        Instant from = Instant.parse("2026-05-25T00:00:00Z");
        Instant to = Instant.parse("2026-05-28T00:00:00Z");
        List<Instant> buckets = service.generateBuckets(from, to, "DAY");
        assertThat(buckets).hasSize(3); // May 25, 26, 27 (to is exclusive)
    }

    @Test
    void generateBuckets_unknownSpan_throwsBadRequest() {
        Instant from = Instant.parse("2026-05-25T00:00:00Z");
        Instant to = Instant.parse("2026-05-28T00:00:00Z");
        assertThatThrownBy(() -> service.generateBuckets(from, to, "HOUR")).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void generateBuckets_monthlySpan_correctBuckets() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-04-01T00:00:00Z");
        List<Instant> buckets = service.generateBuckets(from, to, "MONTH");
        assertThat(buckets).hasSize(3); // Jan, Feb, Mar (to is exclusive)
    }

    @Test
    void generateBuckets_nonMidnightTo_ceilsToNextDay() {
        Instant from = Instant.parse("2026-05-25T00:00:00Z");
        Instant to = Instant.parse("2026-05-27T15:00:00Z");
        List<Instant> buckets = service.generateBuckets(from, to, "DAY");
        assertThat(buckets).hasSize(3); // May 25, 26, 27
        assertThat(buckets.getLast()).isEqualTo(Instant.parse("2026-05-27T00:00:00Z"));
    }

    // -- Time-series tests ------------------------------------------------------

    @Test
    void computeTimeSeries_sessions_returnsBucketedData() {
        Instant from = Instant.parse("2026-05-25T00:00:00Z");
        Instant to = Instant.parse("2026-05-27T00:00:00Z");
        when(repository.findSessionsWithMode(any(), any())).thenReturn(List.of(new Object[] { 1L, Instant.parse("2026-05-25T10:00:00Z"), "CHAT", 1 },
                new Object[] { 2L, Instant.parse("2026-05-25T11:00:00Z"), "CHAT", 0 }, new Object[] { 3L, Instant.parse("2026-05-26T09:00:00Z"), "CHAT", 1 }));
        var result = service.computeTimeSeries(from, to, "DAY", IrisDashboardMetric.SESSIONS);
        assertThat(result.metric()).isEqualTo(IrisDashboardMetric.SESSIONS);
        assertThat(result.entries()).hasSize(2); // May 25, 26 (to is exclusive)
        // First bucket (May 25): 2 total, 1 active
        assertThat(result.entries().getFirst().series().get("total")).isEqualTo(2.0);
        assertThat(result.entries().getFirst().series().get("active")).isEqualTo(1.0);
        // Second bucket (May 26): 1 total, 1 active
        assertThat(result.entries().get(1).series().get("total")).isEqualTo(1.0);
        assertThat(result.entries().get(1).series().get("active")).isEqualTo(1.0);
    }

    @Test
    void computeTimeSeries_messages_returnsBucketedData() {
        Instant from = Instant.parse("2026-05-25T00:00:00Z");
        Instant to = Instant.parse("2026-05-26T00:00:00Z");
        when(repository.findMessagesInRange(any(), any())).thenReturn(List.of(new Object[] { 1L, Instant.parse("2026-05-25T10:00:00Z"), "USER", 100L },
                new Object[] { 2L, Instant.parse("2026-05-25T10:00:05Z"), "LLM", 100L }, new Object[] { 3L, Instant.parse("2026-05-25T11:00:00Z"), "USER", 101L }));
        var result = service.computeTimeSeries(from, to, "DAY", IrisDashboardMetric.MESSAGES);
        assertThat(result.entries()).hasSize(1); // May 25 (to is exclusive)
        assertThat(result.entries().getFirst().series().get("user")).isEqualTo(2.0);
        assertThat(result.entries().getFirst().series().get("llm")).isEqualTo(1.0);
        assertThat(result.entries().getFirst().series().get("total")).isEqualTo(3.0);
    }

    @Test
    void computeTimeSeries_tokenCost_returnsBucketedData() {
        Instant from = Instant.parse("2026-05-25T00:00:00Z");
        Instant to = Instant.parse("2026-05-26T00:00:00Z");
        when(repository.findTokenCostWithTimestamps(any(), any()))
                .thenReturn(List.of(new Object[] { Instant.parse("2026-05-25T10:00:00Z"), 1, 0.50 }, new Object[] { Instant.parse("2026-05-25T11:00:00Z"), 0, 0.25 }));
        var result = service.computeTimeSeries(from, to, "DAY", IrisDashboardMetric.TOKEN_COST);
        assertThat(result.entries().getFirst().series().get("chat")).isEqualTo(0.50);
        assertThat(result.entries().getFirst().series().get("other")).isEqualTo(0.25);
        assertThat(result.entries().getFirst().series().get("total")).isEqualTo(0.75);
    }

    // -- Breakdown tests --------------------------------------------------------

    @Test
    void computeBreakdown_chatMode_groupsByMode() {
        Instant from = Instant.parse("2026-05-25T00:00:00Z");
        Instant to = Instant.parse("2026-05-27T00:00:00Z");
        when(repository.findSessionsWithMode(any(), any())).thenReturn(List.of(new Object[] { 1L, Instant.parse("2026-05-25T10:00:00Z"), "CHAT", 1 },
                new Object[] { 2L, Instant.parse("2026-05-25T11:00:00Z"), "CHAT", 0 }, new Object[] { 3L, Instant.parse("2026-05-26T09:00:00Z"), "TUTOR_SUGGESTION", 1 }));
        when(repository.findUserMessagesWithNextMessageFullRange(any(), any()))
                .thenReturn(List.of(new Object[] { 10L, 1L, Instant.parse("2026-05-25T10:00:00Z"), "LLM", Instant.parse("2026-05-25T10:00:05Z"), "CHAT" },
                        new Object[] { 11L, 3L, Instant.parse("2026-05-26T09:00:00Z"), null, null, "TUTOR_SUGGESTION" }));

        var result = service.computeBreakdown(from, to, IrisDashboardBreakdownDimension.CHAT_MODE);
        assertThat(result).hasSize(2);
    }

    @Test
    void computeBreakdown_course_resolvesNames() {
        Instant from = Instant.parse("2026-05-25T00:00:00Z");
        Instant to = Instant.parse("2026-05-27T00:00:00Z");
        when(repository.findTopCoursesBySessionCount(any(), any(), anyInt())).thenReturn(List.of(new Object[] { 42L, 15L }, new Object[] { 99L, 8L }));

        Course course42 = new Course();
        course42.setId(42L);
        course42.setTitle("Intro to Programming");
        when(courseRepository.findAllById(List.of(42L, 99L))).thenReturn(List.of(course42));

        var result = service.computeBreakdown(from, to, IrisDashboardBreakdownDimension.COURSE);
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().label()).isEqualTo("Intro to Programming");
        assertThat(result.getFirst().values().get("sessions")).isEqualTo(15.0);
        assertThat(result.get(1).label()).isEqualTo("Unknown Course #99");
        assertThat(result.get(1).values().get("sessions")).isEqualTo(8.0);
    }

    @Test
    void computeBreakdown_model_returnsCostAndTokens() {
        Instant from = Instant.parse("2026-05-25T00:00:00Z");
        Instant to = Instant.parse("2026-05-27T00:00:00Z");
        when(repository.computeTokenCostByModel(any(), any())).thenReturn(List.of(new Object[] { "gpt-4o", 100000L, 1.25 }, new Object[] { "claude-sonnet", 50000L, 0.80 }));

        var result = service.computeBreakdown(from, to, IrisDashboardBreakdownDimension.MODEL);
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().label()).isEqualTo("gpt-4o");
        assertThat(result.getFirst().values().get("totalTokens")).isEqualTo(100000.0);
        assertThat(result.getFirst().values().get("costEur")).isEqualTo(1.25);
    }

    // -- mapResults test --------------------------------------------------------

    @Test
    void mapResults_handlesInstantColumns() {
        var rows = List.<Object[]>of(new Object[] { 1L, 100L, Instant.parse("2026-05-26T10:00:00Z"), "LLM", Instant.parse("2026-05-26T10:00:05Z"), "CHAT" });
        var results = service.mapResults(rows);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().userMsgId()).isEqualTo(1L);
        assertThat(results.getFirst().sentAt()).isEqualTo(Instant.parse("2026-05-26T10:00:00Z"));
        assertThat(results.getFirst().nextSender()).isEqualTo(de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender.LLM);
    }

    @Test
    void mapResults_handlesNullNextSender() {
        var rows = List.<Object[]>of(new Object[] { 2L, 101L, Instant.parse("2026-05-26T11:00:00Z"), null, null, "CHAT" });
        var results = service.mapResults(rows);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().nextSender()).isNull();
        assertThat(results.getFirst().nextSentAt()).isNull();
    }

    // -- Helpers ----------------------------------------------------------------

    private void stubAllRepositoryMethods() {
        when(repository.countTotalSessions(any(), any())).thenReturn(0L);
        when(repository.countActiveSessions(any(), any())).thenReturn(0L);
        when(repository.countTotalMessages(any(), any())).thenReturn(0L);
        when(repository.countUniqueUsers(any(), any())).thenReturn(0L);
        when(repository.countThumbsUp(any(), any())).thenReturn(0L);
        when(repository.countThumbsDown(any(), any())).thenReturn(0L);
        when(repository.countTotalLlmMessages(any(), any())).thenReturn(0L);
        when(repository.countSessionsWithThumbsUp(any(), any())).thenReturn(0L);
        when(repository.countSessionsWithThumbsDown(any(), any())).thenReturn(0L);
        when(repository.findUserMessagesWithNextMessageFullRange(any(), any())).thenReturn(List.of());
        when(repository.computeTokenCost(any(), any())).thenReturn(List.of());
    }
}
