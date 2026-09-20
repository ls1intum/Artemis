package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;

class IrisAdminDashboardResourceIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irisadmindashboard";

    private static final String BASE_URL = "/api/iris/admin/dashboard/";

    @Autowired
    private IrisAdminDashboardRepository dashboardRepository;

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private CourseUtilService courseUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getOverview_asAdmin_succeeds() throws Exception {
        request.get(BASE_URL + "overview?from=2026-05-26T00:00:00Z&to=2026-05-27T00:00:00Z", HttpStatus.OK, Object.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getOverview_asStudent_forbidden() throws Exception {
        request.get(BASE_URL + "overview?from=2026-05-26T00:00:00Z&to=2026-05-27T00:00:00Z", HttpStatus.FORBIDDEN, Object.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getOverview_invalidWindow_badRequest() throws Exception {
        request.get(BASE_URL + "overview?from=2026-05-27T00:00:00Z&to=2026-05-26T00:00:00Z", HttpStatus.BAD_REQUEST, Object.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getConfig_asAdmin_succeeds() throws Exception {
        request.get(BASE_URL + "config", HttpStatus.OK, Object.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getTimeSeries_asAdmin_succeeds() throws Exception {
        request.get(BASE_URL + "time-series?from=2026-05-26T00:00:00Z&to=2026-05-27T00:00:00Z&span=DAY&metric=SESSIONS", HttpStatus.OK, Object.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getBreakdown_asAdmin_succeeds() throws Exception {
        request.get(BASE_URL + "breakdown?from=2026-05-26T00:00:00Z&to=2026-05-27T00:00:00Z&dimension=MODEL", HttpStatus.OK, Object.class);
    }

    /**
     * A CTXSWAP marker is a system-generated context-switch marker, not an assistant answer. A user message whose only
     * follow-up is a CTXSWAP marker must still be reported as unanswered by the admin no-response query.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void findUserMessages_userFollowedOnlyByCtxSwapMarker_notCountedAsAnswered() {
        Course course = courseUtilService.createCourse();
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession session = irisChatSessionRepository.save(new IrisChatSession(course, user));

        IrisMessage userMessage = new IrisMessage();
        userMessage.addContent(new IrisTextMessageContent("How does this work?"));
        IrisMessage savedUserMessage = irisMessageService.saveMessage(userMessage, session, IrisMessageSender.USER);

        IrisMessage marker = new IrisMessage();
        marker.addContent(new IrisTextMessageContent("Lecture 1"));
        irisMessageService.saveMessage(marker, session, IrisMessageSender.CTXSWAP);

        Instant from = Instant.now().minus(Duration.ofHours(1));
        Instant to = Instant.now().plus(Duration.ofHours(1));
        List<Object[]> rows = dashboardRepository.findUserMessagesWithNextMessageFullRange(from, to);

        List<Object[]> userRows = rows.stream().filter(row -> ((Number) row[0]).longValue() == savedUserMessage.getId()).toList();
        assertThat(userRows).hasSize(1);
        Object[] row = userRows.getFirst();
        // nextSender (index 3) skips the CTXSWAP marker, so there is no following non-marker message.
        assertThat(row[3]).isNull();
        // hasAssistantResponse (index 6) must be 0: the CTXSWAP marker is not an assistant response.
        assertThat(((Number) row[6]).intValue()).isEqualTo(0);
    }

    /**
     * A COMMAND marker records a client action Iris performed (e.g. a point-out) and is written while the pipeline is
     * still running, so it sits between the user message and the answer. It must not be mistaken for the answer: the
     * response-time lookup has to skip it and report the LLM message that follows, and a user message followed only by
     * a COMMAND marker must still count as unanswered.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void findUserMessages_commandMarkerBetweenUserAndAnswer_reportsLlmAsResponse() {
        Course course = courseUtilService.createCourse();
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession session = irisChatSessionRepository.save(new IrisChatSession(course, user));

        // First exchange: user -> COMMAND marker (written mid-pipeline) -> the actual answer.
        IrisMessage userMessage = new IrisMessage();
        userMessage.addContent(new IrisTextMessageContent("Where did you explain this?"));
        IrisMessage savedUserMessage = irisMessageService.saveMessage(userMessage, session, IrisMessageSender.USER);

        irisMessageService.saveMessage(pointOutMarker(), session, IrisMessageSender.COMMAND);

        IrisMessage answer = new IrisMessage();
        answer.addContent(new IrisTextMessageContent("On slide 3, as highlighted."));
        irisMessageService.saveMessage(answer, session, IrisMessageSender.LLM);

        // Second exchange: user -> COMMAND marker, but no answer ever follows.
        IrisMessage unansweredMessage = new IrisMessage();
        unansweredMessage.addContent(new IrisTextMessageContent("And what about this?"));
        IrisMessage savedUnansweredMessage = irisMessageService.saveMessage(unansweredMessage, session, IrisMessageSender.USER);

        irisMessageService.saveMessage(pointOutMarker(), session, IrisMessageSender.COMMAND);

        Instant from = Instant.now().minus(Duration.ofHours(1));
        Instant to = Instant.now().plus(Duration.ofHours(1));
        List<Object[]> rows = dashboardRepository.findUserMessagesWithNextMessageFullRange(from, to);

        Object[] answeredRow = rows.stream().filter(row -> ((Number) row[0]).longValue() == savedUserMessage.getId()).findFirst().orElseThrow();
        // nextSender (index 3) skips the COMMAND marker sitting in between and reports the answer behind it.
        assertThat(answeredRow[3]).isEqualTo(IrisMessageSender.LLM.name());
        // hasAssistantResponse (index 6) must be 1: the marker did not mask the answer that followed it.
        assertThat(((Number) answeredRow[6]).intValue()).isEqualTo(1);

        Object[] unansweredRow = rows.stream().filter(row -> ((Number) row[0]).longValue() == savedUnansweredMessage.getId()).findFirst().orElseThrow();
        // nextSender (index 3) skips the COMMAND marker, so there is no following non-marker message.
        assertThat(unansweredRow[3]).isNull();
        // hasAssistantResponse (index 6) must be 0: the COMMAND marker is not an assistant response, and the earlier
        // answer sits before this message rather than after it.
        assertThat(((Number) unansweredRow[6]).intValue()).isEqualTo(0);
    }

    private static IrisMessage pointOutMarker() {
        IrisMessage marker = new IrisMessage();
        marker.addContent(new IrisJsonMessageContent(JsonObjectMapper.get().createObjectNode().put("type", "pointOut").put("lectureUnitId", 1L).put("page", 3)));
        return marker;
    }
}
