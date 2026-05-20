package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.domain.dashboard.IrisDashboardMetric;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardBreakdownEntryDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardConfigDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardOverviewDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardTimeSeriesDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;

class IrisAdminDashboardResourceTest extends AbstractIrisChatSessionTest {

    private static final String TEST_PREFIX = "irisadmindashboardresource";

    private static final String BASE_URL = "/api/iris/admin/dashboard";

    private static final ZonedDateTime DASHBOARD_WINDOW_START = ZonedDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    private static final Instant REQUEST_NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void overviewReturnsAggregatedDashboardData() throws Exception {
        ZonedDateTime from = DASHBOARD_WINDOW_START;
        ZonedDateTime to = from.plusMinutes(10);
        saveSessionWithUserAndLlmMessage(from.plusMinutes(1));

        IrisDashboardOverviewDTO overview = request.get(BASE_URL + "/overview", HttpStatus.OK, IrisDashboardOverviewDTO.class, queryParams(from.toInstant(), to.toInstant()));

        assertThat(overview.totalSessions()).isEqualTo(1);
        assertThat(overview.activeSessions()).isEqualTo(1);
        assertThat(overview.engagementRate()).isEqualTo(100);
        assertThat(overview.totalMessages()).isEqualTo(2);
        assertThat(overview.uniqueUsers()).isEqualTo(1);
        assertThat(overview.noResponseRate()).isZero();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void exposesTimeSeriesBreakdownAndConfig() throws Exception {
        ZonedDateTime from = DASHBOARD_WINDOW_START.plusHours(1);
        ZonedDateTime to = from.plusMinutes(10);
        saveSessionWithUserAndLlmMessage(from.plusMinutes(1));

        MultiValueMap<String, String> timeSeriesParams = queryParams(from.toInstant(), to.toInstant());
        timeSeriesParams.add("span", "DAY");
        timeSeriesParams.add("metric", "MESSAGES");
        IrisDashboardTimeSeriesDTO timeSeries = request.get(BASE_URL + "/time-series", HttpStatus.OK, IrisDashboardTimeSeriesDTO.class, timeSeriesParams);
        assertThat(timeSeries.metric()).isEqualTo(IrisDashboardMetric.MESSAGES);
        assertThat(timeSeries.entries()).isNotEmpty();

        MultiValueMap<String, String> breakdownParams = queryParams(from.toInstant(), to.toInstant());
        breakdownParams.add("dimension", "CHAT_MODE");
        List<IrisDashboardBreakdownEntryDTO> breakdown = request.getList(BASE_URL + "/breakdown", HttpStatus.OK, IrisDashboardBreakdownEntryDTO.class, breakdownParams);
        assertThat(breakdown).anySatisfy(entry -> {
            assertThat(entry.name()).isEqualTo("COURSE_CHAT");
            assertThat(entry.metrics()).containsEntry("sessions", 1.0);
        });

        IrisDashboardConfigDTO config = request.get(BASE_URL + "/config", HttpStatus.OK, IrisDashboardConfigDTO.class);
        assertThat(config.maxQueryWindowDays()).isPositive();
        assertThat(config.staleThresholdMinutes()).isPositive();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rejectsNonAdminUsers() throws Exception {
        request.get(BASE_URL + "/overview", HttpStatus.FORBIDDEN, IrisDashboardOverviewDTO.class, queryParams(REQUEST_NOW.minus(Duration.ofDays(1)), REQUEST_NOW));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void rejectsInvalidQueryWindow() throws Exception {
        request.get(BASE_URL + "/overview", HttpStatus.BAD_REQUEST, IrisDashboardOverviewDTO.class, queryParams(REQUEST_NOW.minus(Duration.ofDays(400)), REQUEST_NOW));
    }

    private void saveSessionWithUserAndLlmMessage(ZonedDateTime userMessageTime) {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession session = new IrisChatSession(course, user);
        session.setCreationDate(userMessageTime.minusSeconds(30));
        session.getMessages().add(message(session, IrisMessageSender.USER, userMessageTime, null));
        session.getMessages().add(message(session, IrisMessageSender.LLM, userMessageTime.plusSeconds(4), true));
        irisSessionRepository.saveAndFlush(session);
    }

    private static IrisMessage message(IrisChatSession session, IrisMessageSender sender, ZonedDateTime sentAt, Boolean helpful) {
        IrisMessage message = session.newMessage();
        message.setSender(sender);
        message.setSentAt(sentAt);
        message.setHelpful(helpful);
        return message;
    }

    private static MultiValueMap<String, String> queryParams(Instant from, Instant to) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("from", from.toString());
        params.add("to", to.toString());
        return params;
    }
}
