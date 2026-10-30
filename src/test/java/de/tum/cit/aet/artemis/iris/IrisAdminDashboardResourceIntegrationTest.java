package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardBreakdownEntryDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardConfigDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardOverviewDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardTimeSeriesDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardTimeSeriesEntryDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAdminDashboardRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
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
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LLMTokenUsageService llmTokenUsageService;

    @Autowired
    private LLMTokenUsageTraceTestRepository llmTokenUsageTraceRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 0);
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

    @ParameterizedTest
    @ValueSource(strings = { "overview", "time-series&span=DAY&metric=SESSIONS", "breakdown&dimension=MODEL", "config" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void dashboardReads_asStudent_forbidden(String endpoint) throws Exception {
        request.get(dashboardUrl(endpoint), HttpStatus.FORBIDDEN, Object.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "overview", "time-series&span=DAY&metric=SESSIONS", "breakdown&dimension=MODEL", "config" })
    @WithAnonymousUser
    void dashboardReads_asAnonymous_unauthorized(String endpoint) throws Exception {
        request.get(dashboardUrl(endpoint), HttpStatus.UNAUTHORIZED, Object.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "overview", "time-series&span=DAY&metric=SESSIONS", "breakdown&dimension=MODEL", "breakdown&dimension=CHAT_MODE", "breakdown&dimension=COURSE" })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void dashboardReads_omittedSelectionEqualsAll(String endpoint) throws Exception {
        JsonNode implicit = request.get(dashboardUrl(endpoint), HttpStatus.OK, JsonNode.class);
        JsonNode explicit = request.get(dashboardUrl(endpoint) + "&aiSelection=ALL", HttpStatus.OK, JsonNode.class);
        assertThat(implicit).isEqualTo(explicit);
        request.get(dashboardUrl(endpoint) + "&aiSelection=NO_AI", HttpStatus.BAD_REQUEST, Object.class);
    }

    @ParameterizedTest
    @CsvSource({ "overview,to=2023-02-04T00:00:00Z", "overview,from=2023-02-03T00:00:00Z", "overview,from=invalid&to=2023-02-04T00:00:00Z",
            "time-series,from=2023-02-03T00:00:00Z&to=2023-02-04T00:00:00Z&metric=SESSIONS", "time-series,from=2023-02-03T00:00:00Z&to=2023-02-04T00:00:00Z&span=DAY",
            "time-series,from=2023-02-03T00:00:00Z&to=2023-02-04T00:00:00Z&span=DAY&metric=INVALID",
            "time-series,from=2023-02-03T00:00:00Z&to=2023-02-04T00:00:00Z&span=INVALID&metric=SESSIONS", "breakdown,from=2023-02-03T00:00:00Z&to=2023-02-04T00:00:00Z",
            "breakdown,from=2023-02-03T00:00:00Z&to=2023-02-04T00:00:00Z&dimension=INVALID" })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void dashboardReads_invalidParameters_badRequest(String endpoint, String query) throws Exception {
        request.get(BASE_URL + endpoint + "?" + query, HttpStatus.BAD_REQUEST, Object.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "overview", "time-series&span=DAY&metric=SESSIONS", "breakdown&dimension=MODEL" })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void dashboardReads_missingWindowParameters_badRequest(String endpoint) throws Exception {
        request.get(dashboardUrl(endpoint).replace("from=2023-02-03T00:00:00Z&", ""), HttpStatus.BAD_REQUEST, Object.class);
        request.get(dashboardUrl(endpoint).replace("&to=2023-02-04T00:00:00Z", ""), HttpStatus.BAD_REQUEST, Object.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "overview", "time-series&span=DAY&metric=SESSIONS", "breakdown&dimension=MODEL" })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void dashboardReads_windowBoundaries(String endpoint) throws Exception {
        String url = dashboardUrl(endpoint);
        request.get(url.replace("2023-02-04T00:00:00Z", "2023-02-03T00:00:00Z"), HttpStatus.BAD_REQUEST, Object.class);
        request.get(url.replace("2023-02-04T00:00:00Z", "2023-02-02T00:00:00Z"), HttpStatus.BAD_REQUEST, Object.class);
        // Exactly 90 days is accepted; one extra second exceeds the configured maximum.
        request.get(url.replace("2023-02-04T00:00:00Z", "2023-05-04T00:00:00Z"), HttpStatus.OK, Object.class);
        request.get(url.replace("2023-02-04T00:00:00Z", "2023-05-04T00:00:01Z"), HttpStatus.BAD_REQUEST, Object.class);
        Instant future = de.tum.cit.aet.artemis.core.util.TimeUtil.now().toInstant().plus(Duration.ofDays(3));
        request.get(url.replace("2023-02-03T00:00:00Z", future.minus(Duration.ofDays(1)).toString()).replace("2023-02-04T00:00:00Z", future.toString()), HttpStatus.BAD_REQUEST,
                Object.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "day", "WEEK", "MONTH", "QUARTER", "YEAR" })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void timeSeries_emptyWindow_keepsUtcBucketAndZeroMap(String span) throws Exception {
        JsonNode body = request.get(dashboardUrl("time-series&span=" + span + "&metric=SESSIONS"), HttpStatus.OK, JsonNode.class);
        assertThat(body.get("metric").asString()).isEqualTo("SESSIONS");
        assertThat(body.get("entries").size()).isEqualTo(1);
        JsonNode bucket = body.get("entries").get(0);
        assertThat(bucket.get("bucketStart").asString()).isEqualTo("2023-02-03T00:00:00Z");
        assertThat(bucket.get("series").size()).isEqualTo(2);
        assertThat(bucket.get("series").get("total").asDouble()).isZero();
        assertThat(bucket.get("series").get("active").asDouble()).isZero();
    }

    @ParameterizedTest
    @CsvSource({ "SESSIONS,total active", "MESSAGES,user llm total", "NO_RESPONSE_RATE,rate count", "RESPONSE_TIME,avg p50 p95", "RATINGS,thumbsUp thumbsDown total",
            "TOKEN_COST,chat other total", "ENGAGEMENT,rate activeSessions totalSessions" })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void timeSeries_allMetrics_keepCompleteNumericMaps(String metric, String expectedKeys) throws Exception {
        JsonNode body = request.get(dashboardUrl("time-series&span=DAY&metric=" + metric), HttpStatus.OK, JsonNode.class);
        assertThat(body.get("metric").asString()).isEqualTo(metric);
        assertThat(body.get("entries").size()).isEqualTo(1);
        JsonNode series = body.get("entries").get(0).get("series");
        String[] keys = expectedKeys.split(" ");
        assertThat(series.size()).isEqualTo(keys.length);
        for (String key : keys) {
            assertThat(series.has(key)).as(key).isTrue();
            assertThat(series.get(key).isNumber()).isTrue();
            assertThat(series.get(key).asDouble()).isZero();
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void overview_emptyWindow_serializesAllTwentyZeroMetrics() throws Exception {
        JsonNode body = request.get(dashboardUrl("overview"), HttpStatus.OK, JsonNode.class);
        assertThat(body.size()).isEqualTo(20);
        for (var component : IrisDashboardOverviewDTO.class.getRecordComponents()) {
            assertThat(body.has(component.getName())).as(component.getName()).isTrue();
            assertThat(body.get(component.getName()).isNumber()).isTrue();
            assertThat(body.get(component.getName()).asDouble()).isZero();
        }
        assertThat(request.get(dashboardUrl("breakdown&dimension=MODEL"), HttpStatus.OK, JsonNode.class).isEmpty()).isTrue();
    }

    @Test
    void dashboardSerialization_preservesFalseZeroAndNonEmptyOmissions() {
        var config = new IrisDashboardConfigDTO(90, 5, false, "", false, 0, 0, 0, 0, 0, 0);
        JsonNode body = jsonMapper.valueToTree(config);
        assertThat(body.size()).isEqualTo(10);
        assertThat(body.has("digestCron")).isFalse();
        for (var component : IrisDashboardConfigDTO.class.getRecordComponents()) {
            if (!component.getName().equals("digestCron")) {
                assertThat(body.has(component.getName())).as(component.getName()).isTrue();
            }
        }
        assertThat(body.get("digestEnabled").asBoolean()).isFalse();
        assertThat(body.get("alertEnabled").asBoolean()).isFalse();
        assertThat(body.get("alertNoResponseRateThreshold").asDouble()).isZero();
        JsonNode entry = jsonMapper.valueToTree(new IrisDashboardBreakdownEntryDTO("", Map.of("sessions", 0.0)));
        assertThat(entry.has("label")).isFalse();
        assertThat(entry.get("values").get("sessions").asDouble()).isZero();
        JsonNode bucket = jsonMapper.valueToTree(new IrisDashboardTimeSeriesEntryDTO(Instant.parse("2023-02-03T00:00:00Z"), Map.of("total", 0.0)));
        assertThat(bucket.get("bucketStart").asString()).isEqualTo("2023-02-03T00:00:00Z");
        assertThat(bucket.get("series").get("total").asDouble()).isZero();
    }

    private static String dashboardUrl(String endpoint) {
        String[] parts = endpoint.split("&", 2);
        return BASE_URL + parts[0] + "?from=2023-02-03T00:00:00Z&to=2023-02-04T00:00:00Z" + (parts.length == 2 ? "&" + parts[1] : "");
    }

    /**
     * Cached and cache-write input tokens are part of the input token count but are billed at their own rates. All dashboard cost queries must use the same formula
     * as the course cost.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void costQueries_billCachedAndCacheWriteTokensAtTheirOwnRates() {
        String model = "gpt-6-luna-dashboard-cost-test";
        // 2M input tokens: 1M read from the cache, 0.4M written to it, 0.6M uncached
        var request = new LLMRequest(model, 2_000_000, 1.0f, 100_000, 4.0f, "IRIS_CHAT_COURSE_MESSAGE", 1_000_000, 0.1f, 400_000, 1.25f);
        LLMTokenUsageTrace trace = llmTokenUsageService.saveLLMTokenUsage(List.of(request), LLMServiceType.IRIS, builder -> builder);
        // a window no other test writes to
        ZonedDateTime traceTime = ZonedDateTime.of(2031, 3, 14, 10, 0, 0, 0, ZoneOffset.UTC);
        trace.setTime(traceTime);
        llmTokenUsageTraceRepository.save(trace);
        Instant from = traceTime.minusHours(1).toInstant();
        Instant to = traceTime.plusHours(1).toInstant();
        // 0.6M x 1.0 + 1M x 0.1 + 0.4M x 1.25 + 0.1M x 4.0 = 0.6 + 0.1 + 0.5 + 0.4
        double expectedCost = 1.6;

        List<Object[]> totals = dashboardRepository.computeTokenCost(from, to, "ALL");
        assertThat(totals).hasSize(1);
        assertThat(((Number) totals.getFirst()[1]).doubleValue()).isCloseTo(expectedCost, within(0.001));

        List<Object[]> timeline = dashboardRepository.findTokenCostWithTimestamps(from, to, "ALL");
        assertThat(timeline).hasSize(1);
        assertThat(((Number) timeline.getFirst()[2]).doubleValue()).isCloseTo(expectedCost, within(0.001));

        List<Object[]> byModel = dashboardRepository.computeTokenCostByModel(from, to, "ALL");
        assertThat(byModel).hasSize(1);
        assertThat(byModel.getFirst()[0]).isEqualTo(model);
        assertThat(((Number) byModel.getFirst()[2]).doubleValue()).isCloseTo(expectedCost, within(0.001));
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
        List<Object[]> rows = dashboardRepository.findUserMessagesWithNextMessageFullRange(from, to, "ALL");

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
        List<Object[]> rows = dashboardRepository.findUserMessagesWithNextMessageFullRange(from, to, "ALL");

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

    /**
     * The AI selection filter keeps only users whose current selection matches, on every endpoint the dashboard calls.
     * Without the parameter, or with ALL, the endpoints count every user, including users without a recorded selection.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void aiSelectionFilter_restrictsAllEndpointsToUsersWithThatSelection() throws Exception {
        // a past window no other test writes to; the endpoints reject a 'to' more than one day in the future
        ZonedDateTime start = ZonedDateTime.of(2025, 2, 3, 9, 0, 0, 0, ZoneOffset.UTC);
        String window = "from=2025-02-03T00:00:00Z&to=2025-02-04T00:00:00Z";
        Course course = courseUtilService.createCourse();

        User cloudUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        userUtilService.setAiSelectionDecision(cloudUser, AiSelectionDecision.CLOUD_AI);
        User localUser = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        userUtilService.setAiSelectionDecision(localUser, AiSelectionDecision.LOCAL_AI);
        User undecidedUser = userUtilService.getUserByLogin(TEST_PREFIX + "student3");
        userUtilService.clearAiSelectionDecision(undecidedUser);

        createAnsweredSession(course, cloudUser, start, 10, true, "model-cloud-ai-filter-test", 1.0f);
        createAnsweredSession(course, localUser, start.plusMinutes(10), 200, false, "model-local-ai-filter-test", 0.0f);
        createAnsweredSession(course, undecidedUser, start.plusMinutes(20), 60, null, "model-undecided-ai-filter-test", 2.0f);

        // Overview: no parameter and ALL both count all three users, as before the filter existed
        IrisDashboardOverviewDTO withoutParameter = request.get(BASE_URL + "overview?" + window, HttpStatus.OK, IrisDashboardOverviewDTO.class);
        IrisDashboardOverviewDTO all = request.get(BASE_URL + "overview?" + window + "&aiSelection=ALL", HttpStatus.OK, IrisDashboardOverviewDTO.class);
        assertThat(all).isEqualTo(withoutParameter);
        assertThat(all.totalSessions()).isEqualTo(3);
        assertThat(all.uniqueUsers()).isEqualTo(3);
        assertThat(all.totalMessages()).isEqualTo(6);
        assertThat(all.avgResponseTimeSeconds()).isCloseTo(90.0, within(0.001));
        assertThat(all.totalTokenCostEur()).isCloseTo(3.0, within(0.001));
        assertThat(all.thumbsUpCount()).isEqualTo(1);
        assertThat(all.thumbsDownCount()).isEqualTo(1);
        assertThat(all.sessionsWithThumbsUp()).isEqualTo(1);
        assertThat(all.sessionsWithThumbsDown()).isEqualTo(1);

        IrisDashboardOverviewDTO cloud = request.get(BASE_URL + "overview?" + window + "&aiSelection=CLOUD", HttpStatus.OK, IrisDashboardOverviewDTO.class);
        assertThat(cloud.totalSessions()).isEqualTo(1);
        assertThat(cloud.uniqueUsers()).isEqualTo(1);
        assertThat(cloud.totalMessages()).isEqualTo(2);
        assertThat(cloud.avgResponseTimeSeconds()).isCloseTo(10.0, within(0.001));
        assertThat(cloud.totalTokenCostEur()).isCloseTo(1.0, within(0.001));
        assertThat(cloud.thumbsUpCount()).isEqualTo(1);
        assertThat(cloud.thumbsDownCount()).isZero();
        assertThat(cloud.thumbsUpRatio()).isCloseTo(100.0, within(0.001));
        assertThat(cloud.sessionsWithThumbsUp()).isEqualTo(1);
        assertThat(cloud.sessionsWithThumbsDown()).isZero();

        IrisDashboardOverviewDTO local = request.get(BASE_URL + "overview?" + window + "&aiSelection=LOCAL", HttpStatus.OK, IrisDashboardOverviewDTO.class);
        assertThat(local.totalSessions()).isEqualTo(1);
        assertThat(local.uniqueUsers()).isEqualTo(1);
        assertThat(local.avgResponseTimeSeconds()).isCloseTo(200.0, within(0.001));
        assertThat(local.totalTokenCostEur()).isCloseTo(0.0, within(0.001));
        assertThat(local.thumbsUpCount()).isZero();
        assertThat(local.thumbsDownCount()).isEqualTo(1);
        assertThat(local.thumbsDownRatio()).isCloseTo(100.0, within(0.001));
        assertThat(local.sessionsWithThumbsUp()).isZero();
        assertThat(local.sessionsWithThumbsDown()).isEqualTo(1);

        // Time series: compare defaults with populated data so a wrong implicit filter cannot hide behind an empty result.
        assertThat(singleDaySeries(window, "RATINGS", "&aiSelection=ALL")).isEqualTo(singleDaySeries(window, "RATINGS", ""));
        assertThat(singleDaySeries(window, "RESPONSE_TIME", "&aiSelection=ALL")).isEqualTo(singleDaySeries(window, "RESPONSE_TIME", ""));
        assertThat(singleDayResponseTime(window, "")).isCloseTo(90.0, within(0.001));
        assertThat(singleDayResponseTime(window, "&aiSelection=CLOUD")).isCloseTo(10.0, within(0.001));
        assertThat(singleDayResponseTime(window, "&aiSelection=LOCAL")).isCloseTo(200.0, within(0.001));
        assertThat(singleDaySeries(window, "RATINGS", "")).containsEntry("thumbsUp", 1.0).containsEntry("thumbsDown", 1.0).containsEntry("total", 3.0);
        assertThat(singleDaySeries(window, "RATINGS", "&aiSelection=CLOUD")).containsEntry("thumbsUp", 1.0).containsEntry("thumbsDown", 0.0).containsEntry("total", 1.0);
        assertThat(singleDaySeries(window, "RATINGS", "&aiSelection=LOCAL")).containsEntry("thumbsUp", 0.0).containsEntry("thumbsDown", 1.0).containsEntry("total", 1.0);

        // Breakdowns: sessions per course and token usage per model
        assertThat(courseSessions(window, "&aiSelection=ALL", course)).isEqualTo(courseSessions(window, "", course));
        assertThat(models(window, "&aiSelection=ALL")).containsExactlyElementsOf(models(window, ""));
        assertThat(courseSessions(window, "", course)).isEqualTo(3.0);
        assertThat(courseSessions(window, "&aiSelection=CLOUD", course)).isEqualTo(1.0);
        assertThat(courseSessions(window, "&aiSelection=LOCAL", course)).isEqualTo(1.0);

        assertThat(models(window, "")).containsExactlyInAnyOrder("model-cloud-ai-filter-test", "model-local-ai-filter-test", "model-undecided-ai-filter-test");
        assertThat(models(window, "&aiSelection=CLOUD")).containsExactly("model-cloud-ai-filter-test");
        assertThat(models(window, "&aiSelection=LOCAL")).containsExactly("model-local-ai-filter-test");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void aiSelectionFilter_unknownValue_badRequest() throws Exception {
        request.get(BASE_URL + "overview?from=2026-05-26T00:00:00Z&to=2026-05-27T00:00:00Z&aiSelection=NO_AI", HttpStatus.BAD_REQUEST, Object.class);
    }

    /**
     * Creates a chat session with one user message and one answer after {@code responseSeconds}, rated {@code helpful}, plus a
     * token usage trace for the answer that costs {@code costEur}.
     */
    private void createAnsweredSession(Course course, User user, ZonedDateTime createdAt, int responseSeconds, Boolean helpful, String model, float costEur) {
        IrisChatSession newSession = new IrisChatSession(course, user);
        newSession.setCreationDate(createdAt);
        IrisChatSession session = irisChatSessionRepository.save(newSession);

        ZonedDateTime askedAt = createdAt.plusMinutes(1);
        ZonedDateTime answeredAt = askedAt.plusSeconds(responseSeconds);
        saveMessage(session, IrisMessageSender.USER, "Question", askedAt, null);
        IrisMessage savedAnswer = saveMessage(session, IrisMessageSender.LLM, "Answer", answeredAt, helpful);

        // 1M output tokens at costEur per million
        var llmRequest = new LLMRequest(model, 0, 0f, 1_000_000, costEur, "IRIS_CHAT_COURSE_MESSAGE");
        LLMTokenUsageTrace trace = llmTokenUsageService.saveLLMTokenUsage(List.of(llmRequest), LLMServiceType.IRIS,
                builder -> builder.withIrisMessageID(savedAnswer.getId()).withUser(user.getId()));
        trace.setTime(answeredAt);
        llmTokenUsageTraceRepository.save(trace);
    }

    /**
     * Appending a message always stamps it with the current time, so the timestamp and rating are set afterwards.
     */
    private IrisMessage saveMessage(IrisChatSession session, IrisMessageSender sender, String text, ZonedDateTime sentAt, Boolean helpful) {
        IrisMessage message = new IrisMessage();
        message.addContent(new IrisTextMessageContent(text));
        IrisMessage saved = irisMessageService.saveMessage(message, session, sender);
        saved.setSentAt(sentAt);
        saved.setHelpful(helpful);
        return irisMessageRepository.save(saved);
    }

    private double singleDayResponseTime(String window, String filter) throws Exception {
        return singleDaySeries(window, "RESPONSE_TIME", filter).get("avg");
    }

    private Map<String, Double> singleDaySeries(String window, String metric, String filter) throws Exception {
        IrisDashboardTimeSeriesDTO series = request.get(BASE_URL + "time-series?" + window + "&span=DAY&metric=" + metric + filter, HttpStatus.OK,
                IrisDashboardTimeSeriesDTO.class);
        assertThat(series.entries()).hasSize(1);
        return series.entries().getFirst().series();
    }

    private double courseSessions(String window, String filter, Course course) throws Exception {
        List<IrisDashboardBreakdownEntryDTO> entries = request.getList(BASE_URL + "breakdown?" + window + "&dimension=COURSE" + filter, HttpStatus.OK,
                IrisDashboardBreakdownEntryDTO.class);
        return entries.stream().filter(entry -> entry.label().equals(course.getTitle())).map(IrisDashboardBreakdownEntryDTO::values).map(values -> values.get("sessions"))
                .findFirst().orElse(0.0);
    }

    private List<String> models(String window, String filter) throws Exception {
        List<IrisDashboardBreakdownEntryDTO> entries = request.getList(BASE_URL + "breakdown?" + window + "&dimension=MODEL" + filter, HttpStatus.OK,
                IrisDashboardBreakdownEntryDTO.class);
        return entries.stream().map(IrisDashboardBreakdownEntryDTO::label).toList();
    }

    private static IrisMessage pointOutMarker() {
        IrisMessage marker = new IrisMessage();
        marker.addContent(new IrisJsonMessageContent(JsonObjectMapper.get().createObjectNode().put("type", "pointOut").put("lectureUnitId", 1L).put("page", 3)));
        return marker;
    }
}
