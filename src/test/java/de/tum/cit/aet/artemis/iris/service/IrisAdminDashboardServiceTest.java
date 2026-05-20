package de.tum.cit.aet.artemis.iris.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.domain.dashboard.IrisDashboardMetric;
import de.tum.cit.aet.artemis.iris.domain.dashboard.IrisDashboardSessionType;
import de.tum.cit.aet.artemis.iris.domain.dashboard.IrisDashboardSpan;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardOverviewDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardUserMessageResultDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository.MessageRow;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository.SessionRow;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository.TokenUsageRow;

@ExtendWith(MockitoExtension.class)
class IrisAdminDashboardServiceTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");

    private static final Instant TO = Instant.parse("2026-01-01T01:00:00Z");

    @Mock
    private IrisAdminDashboardRepository repository;

    private IrisAdminDashboardService service;

    private IrisDashboardProperties properties;

    @BeforeEach
    void setUp() {
        properties = new IrisDashboardProperties();
        service = new IrisAdminDashboardService(repository, properties, new ConcurrentMapCacheManager("iris-dashboard-data"));
        TimeUtil.setClock(Clock.fixed(Instant.parse("2026-01-01T02:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
    }

    @Test
    void computesOverviewFromJavaAggregations() {
        mockDashboardRows(FROM, TO, sessions(), messages(), userMessageResults(), tokenUsage());

        IrisDashboardOverviewDTO overview = service.getOverview(FROM, TO, null);

        assertThat(overview.totalSessions()).isEqualTo(2);
        assertThat(overview.activeSessions()).isEqualTo(2);
        assertThat(overview.engagementRate()).isEqualTo(100.0);
        assertThat(overview.totalMessages()).isEqualTo(5);
        assertThat(overview.uniqueUsers()).isEqualTo(2);
        assertThat(overview.userMessageCount()).isEqualTo(3);
        assertThat(overview.eligibleSessions()).isEqualTo(2);
        assertThat(overview.noResponseMessageCount()).isEqualTo(1);
        assertThat(overview.noResponseSessionCount()).isEqualTo(1);
        assertThat(overview.noResponseRate()).isEqualTo(50.0);
        assertThat(overview.thumbsUpRatio()).isEqualTo(50.0);
        assertThat(overview.thumbsDownRatio()).isEqualTo(50.0);
        assertThat(overview.thumbsUpAbsoluteRate()).isEqualTo(50.0);
        assertThat(overview.thumbsDownAbsoluteRate()).isEqualTo(50.0);
        assertThat(overview.sessionsWithThumbsUp()).isEqualTo(1);
        assertThat(overview.sessionsWithThumbsDown()).isEqualTo(1);
        assertThat(overview.averageResponseTimeSeconds()).isEqualTo(12.0);
        assertThat(overview.p50ResponseTimeSeconds()).isEqualTo(12.0);
        assertThat(overview.p95ResponseTimeSeconds()).isEqualTo(13.8);
        assertThat(overview.totalTokenCostEur()).isEqualTo(0.003);
    }

    @Test
    void returnsZeroRatesForEmptyWindow() {
        mockDashboardRows(FROM, TO, List.of(), List.of(), List.of(), List.of());

        IrisDashboardOverviewDTO overview = service.getOverview(FROM, TO, null);

        assertThat(overview.totalSessions()).isZero();
        assertThat(overview.activeSessions()).isZero();
        assertThat(overview.engagementRate()).isZero();
        assertThat(overview.noResponseRate()).isZero();
        assertThat(overview.thumbsUpRatio()).isZero();
        assertThat(overview.averageResponseTimeSeconds()).isZero();
        assertThat(overview.totalTokenCostEur()).isZero();
    }

    @Test
    void excludesRecentUserMessagesFromNoResponseRate() {
        TimeUtil.setClock(Clock.fixed(Instant.parse("2026-01-01T01:00:00Z"), ZoneOffset.UTC));
        var recentUserMessage = new IrisDashboardUserMessageResultDTO(1L, 1L, 1L, "COURSE_CHAT", zdt("2026-01-01T00:58:00Z"), null, null);
        mockDashboardRows(FROM, TO, List.of(session(1L, "COURSE_CHAT", "2026-01-01T00:00:00Z")), List.of(message(1L, 1L, "COURSE_CHAT", "USER", "2026-01-01T00:58:00Z", null, 10L)),
                List.of(recentUserMessage), List.of());

        IrisDashboardOverviewDTO overview = service.getOverview(FROM, TO, null);

        assertThat(overview.userMessageCount()).isEqualTo(1);
        assertThat(overview.eligibleSessions()).isZero();
        assertThat(overview.noResponseMessageCount()).isZero();
        assertThat(overview.noResponseRate()).isZero();
    }

    @Test
    void zeroFillsTimeSeriesBuckets() {
        mockDashboardRows(FROM, TO, sessions(), messages(), userMessageResults(), tokenUsage());

        var series = service.getTimeSeries(FROM, TO, IrisDashboardSpan.DAY, IrisDashboardMetric.MESSAGES, null);

        assertThat(series.entries()).hasSize(1);
        assertThat(series.entries().getFirst().series()).containsEntry("USER", 3.0).containsEntry("LLM", 2.0).containsEntry("ARTIFACT", 0.0);
    }

    @Test
    void includesContinuingSessionsInEngagementDenominator() {
        mockDashboardRows(FROM, TO, List.of(session(7L, "COURSE_CHAT", "2025-12-31T23:00:00Z")), List.of(message(1L, 7L, "COURSE_CHAT", "USER", "2026-01-01T00:10:00Z", null, 10L)),
                List.of(), List.of());

        IrisDashboardOverviewDTO overview = service.getOverview(FROM, TO, null);
        var series = service.getTimeSeries(FROM, TO, IrisDashboardSpan.DAY, IrisDashboardMetric.ENGAGEMENT, null);

        assertThat(overview.totalSessions()).isEqualTo(1);
        assertThat(overview.activeSessions()).isEqualTo(1);
        assertThat(overview.engagementRate()).isEqualTo(100.0);
        assertThat(series.entries().getFirst().series()).containsEntry("ENGAGEMENT_RATE", 100.0);
    }

    @Test
    void rejectsWindowsLargerThanConfiguredMaximum() {
        properties.setMaxQueryWindowDays(1);

        assertThatThrownBy(() -> service.getOverview(FROM, FROM.plusSeconds(2 * 24 * 60 * 60), null)).isInstanceOf(ResponseStatusException.class).extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsBlankDashboardSessionTypeParameter() {
        assertThatThrownBy(() -> IrisDashboardSessionType.fromRequestParameter(" ")).isInstanceOf(ResponseStatusException.class).extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void mockDashboardRows(Instant from, Instant to, List<SessionRow> sessions, List<MessageRow> messages, List<IrisDashboardUserMessageResultDTO> userMessageResults,
            List<TokenUsageRow> tokenUsage) {
        ZonedDateTime fromZoned = from.atZone(ZoneOffset.UTC);
        ZonedDateTime toZoned = to.atZone(ZoneOffset.UTC);
        when(repository.findSessions(eq(fromZoned), eq(toZoned), isNull())).thenReturn(sessions);
        when(repository.findMessages(eq(fromZoned), eq(toZoned), isNull())).thenReturn(messages);
        when(repository.findUserMessageResults(eq(fromZoned), eq(toZoned), isNull())).thenReturn(userMessageResults);
        when(repository.findTokenUsage(eq(fromZoned), eq(toZoned), isNull())).thenReturn(tokenUsage);
    }

    private static List<SessionRow> sessions() {
        return List.of(session(1L, "COURSE_CHAT", "2026-01-01T00:00:00Z"), session(3L, "TEXT_EXERCISE_CHAT", "2026-01-01T00:05:00Z"));
    }

    private static List<MessageRow> messages() {
        return List.of(message(1L, 1L, "COURSE_CHAT", "USER", "2026-01-01T00:10:00Z", null, 10L), message(2L, 1L, "COURSE_CHAT", "LLM", "2026-01-01T00:10:10Z", true, 10L),
                message(3L, 3L, "TEXT_EXERCISE_CHAT", "USER", "2026-01-01T00:15:00Z", null, 11L), message(4L, 3L, "TEXT_EXERCISE_CHAT", "USER", "2026-01-01T00:15:16Z", null, 11L),
                message(5L, 3L, "TEXT_EXERCISE_CHAT", "LLM", "2026-01-01T00:15:30Z", false, 11L));
    }

    private static List<IrisDashboardUserMessageResultDTO> userMessageResults() {
        return List.of(new IrisDashboardUserMessageResultDTO(1L, 1L, 1L, "COURSE_CHAT", zdt("2026-01-01T00:10:00Z"), "LLM", zdt("2026-01-01T00:10:10Z")),
                new IrisDashboardUserMessageResultDTO(3L, 3L, 1L, "TEXT_EXERCISE_CHAT", zdt("2026-01-01T00:15:00Z"), "USER", zdt("2026-01-01T00:15:16Z")),
                new IrisDashboardUserMessageResultDTO(4L, 3L, 1L, "TEXT_EXERCISE_CHAT", zdt("2026-01-01T00:15:16Z"), "LLM", zdt("2026-01-01T00:15:30Z")));
    }

    private static List<TokenUsageRow> tokenUsage() {
        return List.of(new TokenUsageRow(1L, zdt("2026-01-01T00:11:00Z"), 1L, "Course", "COURSE_CHAT", "gpt-test", 1000, 1000, 0.003, true));
    }

    private static SessionRow session(long id, String sessionType, String creationDate) {
        return new SessionRow(id, sessionType, 1L, "Course", zdt(creationDate));
    }

    private static MessageRow message(long id, long sessionId, String sessionType, String sender, String sentAt, Boolean helpful, Long userId) {
        return new MessageRow(id, sessionId, sessionType, 1L, "Course", sender, zdt(sentAt), helpful, userId);
    }

    private static ZonedDateTime zdt(String value) {
        return Instant.parse(value).atZone(ZoneOffset.UTC);
    }
}
