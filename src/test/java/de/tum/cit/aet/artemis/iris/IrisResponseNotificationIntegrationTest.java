package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.util.ArtemisApp;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.util.IrisChatSessionFactory;
import de.tum.cit.aet.artemis.iris.util.IrisMessageFactory;
import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.CourseNotification;
import de.tum.cit.aet.artemis.notification.domain.CourseNotificationParameter;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.IrisResponseNotification;
import de.tum.cit.aet.artemis.notification.test_repository.CourseNotificationParameterTestRepository;
import de.tum.cit.aet.artemis.notification.test_repository.CourseNotificationTestRepository;

/**
 * Integration tests for the push-notification flow that fires after an asynchronous Iris answer
 * (see {@code IrisChatSessionService#notifyUserOfIrisResponse}). Covers:
 * <ul>
 * <li>persisting the originating Artemis app (resolved from the request {@code User-Agent}) on the user message,</li>
 * <li>creating an {@link IrisResponseNotification} only when the triggering message came from the iOS app and the
 * chat is not open anywhere,</li>
 * <li>suppressing the notification for web browsers, the Android app and unrecognized clients, and while the session
 * is open live.</li>
 * </ul>
 * The originating app is detected exactly like the login-notification flow — from the standard {@code User-Agent}
 * header — so no dedicated client header is required. The session-presence check is spied so both branches
 * (open / not open) can be exercised deterministically.
 */
class IrisResponseNotificationIntegrationTest extends AbstractIrisChatSessionTest {

    private static final String TEST_PREFIX = "irisresponsenotification";

    /** User-Agent sent by the native iOS app (format: "Artemis/<build> CFNetwork/<version> Darwin/<version>"). */
    private static final String IOS_USER_AGENT = "Artemis/20250524013147 CFNetwork/3826.500.131 Darwin/24.5.0";

    /** The Android app uses ktor (https://ktor.io/) as its HTTP client. */
    private static final String ANDROID_USER_AGENT = "ktor-client";

    /** A regular desktop browser User-Agent (recognized as a browser, i.e. not an Artemis app). */
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0";

    private static final Short IRIS_RESPONSE_TYPE = (short) IrisResponseNotification.class.getAnnotation(CourseNotificationType.class).value();

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private CourseNotificationTestRepository courseNotificationTestRepository;

    @Autowired
    private CourseNotificationParameterTestRepository courseNotificationParameterRepository;

    private AtomicBoolean pipelineDone;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    void initState() {
        pipelineDone = new AtomicBoolean(false);
    }

    // =========================================================================
    // Originating-app persistence
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createMessage_persistsIosSenderOrigin() throws Exception {
        IrisChatSession session = createSession("student1");
        mockChatResponse(_ -> pipelineDone.set(true));

        request.postWithoutResponseBody(messagesUrl(session), IrisMessageFactory.createIrisMessageForSessionWithContent(session), HttpStatus.CREATED,
                userAgentHeaders(IOS_USER_AGENT));
        await().until(pipelineDone::get);

        assertThat(userMessageOrigin(session)).isEqualTo(ArtemisApp.IOS);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resendMessage_updatesSenderOriginFromUserAgent() throws Exception {
        IrisChatSession session = createSession("student1");
        IrisMessage userMessage = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.USER);
        userMessage.setSenderOrigin(ArtemisApp.ANDROID);
        irisMessageRepository.save(userMessage);

        mockChatResponse(_ -> pipelineDone.set(true));
        request.postWithoutResponseBody(messagesUrl(session) + "/" + userMessage.getId() + "/resend", null, HttpStatus.OK, userAgentHeaders(IOS_USER_AGENT));
        await().until(pipelineDone::get);

        assertThat(irisMessageRepository.findById(userMessage.getId()).orElseThrow().getSenderOrigin()).isEqualTo(ArtemisApp.IOS);
    }

    // =========================================================================
    // Notification gating
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void iosMessage_whenSessionNotOpen_createsNotificationWithStrippedPreview() throws Exception {
        IrisChatSession session = createSession("student1");
        doReturn(false).when(irisSessionPresenceService).isSessionOpenAnywhere(anyString(), anyLong());
        long before = irisResponseNotificationCount();

        runChatWithAssistantAnswer(session, "**Hello** _World_", IOS_USER_AGENT);

        assertThat(irisResponseNotificationCount()).isEqualTo(before + 1);
        assertThat(latestNotificationParameter("messagePreview")).isEqualTo("Hello World");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void iosMessage_whenSessionOpen_doesNotCreateNotification() throws Exception {
        IrisChatSession session = createSession("student1");
        doReturn(true).when(irisSessionPresenceService).isSessionOpenAnywhere(anyString(), anyLong());
        long before = irisResponseNotificationCount();

        runChatWithAssistantAnswer(session, "Hello World", IOS_USER_AGENT);

        assertThat(irisResponseNotificationCount()).isEqualTo(before);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void browserMessage_doesNotCreateNotification() throws Exception {
        IrisChatSession session = createSession("student1");
        doReturn(false).when(irisSessionPresenceService).isSessionOpenAnywhere(anyString(), anyLong());
        long before = irisResponseNotificationCount();

        runChatWithAssistantAnswer(session, "Hello World", BROWSER_USER_AGENT);

        assertThat(irisResponseNotificationCount()).isEqualTo(before);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void androidMessage_doesNotCreateNotification() throws Exception {
        IrisChatSession session = createSession("student1");
        doReturn(false).when(irisSessionPresenceService).isSessionOpenAnywhere(anyString(), anyLong());
        long before = irisResponseNotificationCount();

        runChatWithAssistantAnswer(session, "Hello World", ANDROID_USER_AGENT);

        assertThat(irisResponseNotificationCount()).isEqualTo(before);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void messageWithoutUserAgent_doesNotCreateNotification() throws Exception {
        IrisChatSession session = createSession("student1");
        doReturn(false).when(irisSessionPresenceService).isSessionOpenAnywhere(anyString(), anyLong());
        long before = irisResponseNotificationCount();

        runChatWithAssistantAnswer(session, "Hello World", null);

        assertThat(irisResponseNotificationCount()).isEqualTo(before);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private IrisChatSession createSession(String studentLogin) {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + studentLogin);
        return irisChatSessionRepository.save(IrisChatSessionFactory.createCourseChatSessionForUser(course, user));
    }

    /**
     * Posts a user message with the given {@code User-Agent} and lets the mocked Pyris pipeline answer with the given
     * text, waiting until the answer (and the resulting notification side effect) has been processed.
     */
    private void runChatWithAssistantAnswer(IrisChatSession session, String assistantAnswer, String userAgent) throws Exception {
        mockChatResponse(dto -> {
            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), assistantAnswer, dto.initialStages()));
            pipelineDone.set(true);
        });
        request.postWithoutResponseBody(messagesUrl(session), IrisMessageFactory.createIrisMessageForSessionWithContent(session), HttpStatus.CREATED, userAgentHeaders(userAgent));
        await().until(pipelineDone::get);
    }

    private ArtemisApp userMessageOrigin(IrisChatSession session) {
        return irisSessionRepository.findByIdWithMessagesElseThrow(session.getId()).getMessages().stream().filter(message -> message.getSender() == IrisMessageSender.USER)
                .findFirst().orElseThrow().getSenderOrigin();
    }

    private long irisResponseNotificationCount() {
        return courseNotificationTestRepository.findAll().stream().filter(notification -> IRIS_RESPONSE_TYPE.equals(notification.getType())).count();
    }

    private String latestNotificationParameter(String key) {
        CourseNotification latest = courseNotificationTestRepository.findAll().stream().filter(notification -> IRIS_RESPONSE_TYPE.equals(notification.getType()))
                .max(Comparator.comparing(CourseNotification::getId)).orElseThrow();
        Set<CourseNotificationParameter> parameters = courseNotificationParameterRepository.findByCourseNotificationIdEquals(latest.getId());
        return parameters.stream().filter(parameter -> parameter.getKey().equals(key)).map(CourseNotificationParameter::getValue).findFirst().orElseThrow();
    }

    private HttpHeaders userAgentHeaders(String userAgent) {
        HttpHeaders headers = new HttpHeaders();
        if (userAgent != null) {
            headers.add(HttpHeaders.USER_AGENT, userAgent);
        }
        return headers;
    }

    private void mockChatResponse(Consumer<PyrisChatPipelineExecutionDTO> consumer) {
        irisRequestMockProvider.mockProgrammingExerciseChatResponse(consumer);
    }

    private void sendStatus(String jobId, String result, List<PyrisStageDTO> stages) throws Exception {
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobId))));
        request.postWithoutResponseBody("/api/iris/internal/pipelines/chat/runs/" + jobId + "/status", new PyrisChatStatusUpdateDTO(result, stages, null, null, null, null, null),
                HttpStatus.OK, headers);
    }

    private static String messagesUrl(IrisChatSession session) {
        return "/api/iris/sessions/" + session.getId() + "/messages";
    }
}
