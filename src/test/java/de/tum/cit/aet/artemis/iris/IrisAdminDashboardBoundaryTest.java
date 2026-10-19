package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardBreakdownDimension;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardMetric;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository;
import de.tum.cit.aet.artemis.iris.service.IrisAdminDashboardService;

class IrisAdminDashboardBoundaryTest {

    private static final Instant FROM = Instant.parse("2026-05-25T00:00:00Z");

    private static final Instant MIDDLE = FROM.plusSeconds(86400);

    private static final Instant TO = MIDDLE.plusSeconds(86400);

    private IrisAdminDashboardRepository repository;

    private IrisAdminDashboardService service;

    @BeforeEach
    void setUp() {
        repository = mock(IrisAdminDashboardRepository.class);
        service = new IrisAdminDashboardService(repository, new IrisDashboardProperties(), mock(CourseTestRepository.class));
        TimeUtil.setClock(Clock.fixed(TO.plusSeconds(86400), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
    }

    @ParameterizedTest
    @EnumSource(IrisDashboardMetric.class)
    void bucketsIncludeStartExcludeEndAndIgnoreRowsOutsideRange(IrisDashboardMetric metric) {
        Map<String, Double> expected;
        switch (metric) {
            case SESSIONS, ENGAGEMENT -> {
                when(repository.findSessionsWithMode(FROM, TO)).thenReturn(List.of(new Object[] { 1L, FROM.minusSeconds(1), "CHAT", 1 }, new Object[] { 2L, FROM, "CHAT", 1 },
                        new Object[] { 3L, FROM.plusSeconds(1), "CHAT", 0 }, new Object[] { 4L, TO, "CHAT", 1 }));
                expected = metric == IrisDashboardMetric.SESSIONS ? Map.of("total", 2.0, "active", 1.0) : Map.of("rate", 50.0, "activeSessions", 1.0, "totalSessions", 2.0);
            }
            case MESSAGES -> {
                when(repository.findMessagesInRange(FROM, TO)).thenReturn(List.of(new Object[] { 1L, FROM.minusSeconds(1), "USER" }, new Object[] { 2L, FROM, "USER" },
                        new Object[] { 3L, FROM.plusSeconds(1), "LLM" }, new Object[] { 4L, FROM.plusSeconds(2), "SYSTEM" }, new Object[] { 5L, TO, "LLM" }));
                expected = Map.of("user", 1.0, "llm", 1.0, "total", 2.0);
            }
            case RATINGS -> {
                when(repository.findLlmMessagesWithRatings(FROM, TO)).thenReturn(List.of(new Object[] { 1L, FROM.minusSeconds(1), true }, new Object[] { 2L, FROM, true },
                        new Object[] { 3L, FROM.plusSeconds(1), false }, new Object[] { 4L, FROM.plusSeconds(2), null }, new Object[] { 5L, TO, true }));
                expected = Map.of("thumbsUp", 1.0, "thumbsDown", 1.0, "total", 3.0);
            }
            case TOKEN_COST -> {
                when(repository.findTokenCostWithTimestamps(FROM, TO)).thenReturn(List.of(new Object[] { FROM.minusSeconds(1), 1, 99.0 }, new Object[] { FROM, 1, 0.25 },
                        new Object[] { FROM.plusSeconds(1), 0, 0.75 }, new Object[] { TO, 1, 99.0 }));
                expected = Map.of("chat", 0.25, "other", 0.75, "total", 1.0);
            }
            case NO_RESPONSE_RATE -> {
                when(repository.findUserMessagesWithNextMessageFullRange(FROM, TO))
                        .thenReturn(List.of(new Object[] { 1L, 1L, FROM.minusSeconds(1), null, null, null, 0 }, new Object[] { 2L, 1L, FROM, null, null, null, null },
                                new Object[] { 3L, 2L, FROM.plusSeconds(1), "LLM", FROM.plusSeconds(2), "CHAT", 1 }, new Object[] { 4L, 3L, TO, null, null, "CHAT", 0 }));
                expected = Map.of("rate", 50.0, "count", 1.0);
            }
            case RESPONSE_TIME -> {
                when(repository.findUserMessagesWithNextMessageFullRange(FROM, TO)).thenReturn(List.of(
                        new Object[] { 1L, 1L, FROM.minusSeconds(1), "LLM", FROM.plusSeconds(99), "CHAT", 1 }, new Object[] { 2L, 1L, FROM, "LLM", FROM.plusSeconds(2), "CHAT", 1 },
                        new Object[] { 3L, 1L, FROM.plusSeconds(1), "USER", FROM.plusSeconds(3), "CHAT", 0 }, new Object[] { 4L, 1L, FROM.plusSeconds(2), "LLM", null, "CHAT", 0 },
                        new Object[] { 5L, 1L, FROM.plusSeconds(3), "LLM", FROM.plusSeconds(2), "CHAT", 1 }, new Object[] { 6L, 1L, TO, "LLM", TO.plusSeconds(99), "CHAT", 1 }));
                expected = Map.of("avg", 2.0, "p50", 2.0, "p95", 2.0);
            }
            default -> throw new IllegalArgumentException("Unhandled metric " + metric);
        }
        var series = service.computeTimeSeries(FROM, TO, "day", metric);
        assertThat(series.entries()).hasSize(2);
        assertThat(series.entries().get(0).series()).containsExactlyInAnyOrderEntriesOf(expected);
        assertThat(series.entries().get(1).series().values()).allMatch(value -> value == 0.0);
    }

    @Test
    void chatModeBreakdownAggregatesUnknownAndInactiveSessionsWithoutEligibleMessages() {
        when(repository.findSessionsWithMode(FROM, TO)).thenReturn(
                List.of(new Object[] { 1L, FROM, null, 0 }, new Object[] { 2L, FROM, "CHAT", 1 }, new Object[] { 3L, FROM, "CHAT", 0 }, new Object[] { 4L, FROM, "EMPTY", 0 }));
        when(repository.findUserMessagesWithNextMessageFullRange(FROM, TO))
                .thenReturn(List.of(new Object[] { 1L, 1L, FROM, null, null, null, 0 }, new Object[] { 2L, 2L, FROM, "LLM", FROM.plusSeconds(1), "CHAT", 1 }));
        var result = service.computeBreakdown(FROM, TO, IrisDashboardBreakdownDimension.CHAT_MODE);
        assertThat(result).filteredOn(entry -> entry.label().equals("UNKNOWN")).singleElement()
                .satisfies(entry -> assertThat(entry.values()).containsEntry("noResponseRate", 100.0).containsEntry("activeSessions", 0.0));
        assertThat(result).filteredOn(entry -> entry.label().equals("CHAT")).singleElement()
                .satisfies(entry -> assertThat(entry.values()).containsEntry("sessions", 2.0).containsEntry("activeSessions", 1.0).containsEntry("noResponseRate", 0.0));
        assertThat(result).filteredOn(entry -> entry.label().equals("EMPTY")).singleElement()
                .satisfies(entry -> assertThat(entry.values()).containsEntry("messages", 0.0).containsEntry("noResponseRate", 0.0));
    }

    @Test
    void nativeRowsRejectInvalidTemporalValuesAndTreatOnlyOneAsAssistantReply() {
        assertThatThrownBy(() -> service.mapResults(List.<Object[]>of(new Object[] { 1L, 1L, null, null, null, null, 0 }))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot convert null to Instant");
        assertThatThrownBy(() -> service.mapResults(List.<Object[]>of(new Object[] { 1L, 1L, "invalid", null, null, null, 0 }))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("java.lang.String");
        var mapped = service.mapResults(List.<Object[]>of(new Object[] { 1L, 1L, FROM, null, null, null, 2 }));
        assertThat(mapped.getFirst().hasAssistantResponse()).isFalse();
        assertThat(mapped.getFirst().modeLabel()).isNull();
    }

    @Test
    void overviewExcludesFreshMessagesAndNegativeResponseTimes() {
        Instant fresh = TO.plusSeconds(86400);
        when(repository.findUserMessagesWithNextMessageFullRange(any(), any())).thenReturn(List.of(new Object[] { 1L, 1L, fresh, null, null, "CHAT", 0 },
                new Object[] { 2L, 2L, FROM, "LLM", FROM.minusSeconds(1), "CHAT", 1 }, new Object[] { 3L, 3L, FROM, "LLM", null, "CHAT", 0 }));
        var result = service.computeOverview(FROM, fresh);
        assertThat(result.noResponseRate()).isEqualTo(50.0);
        assertThat(result.noResponseMessageCount()).isEqualTo(1);
        assertThat(result.avgResponseTimeSeconds()).isZero();
    }
}
