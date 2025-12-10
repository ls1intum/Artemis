package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.DONE;
import static de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.IN_PROGRESS;
import static de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.NOT_STARTED;
import static de.tum.cit.aet.artemis.iris.util.IrisChatWebsocketMatchers.messageDTO;
import static de.tum.cit.aet.artemis.iris.util.IrisChatWebsocketMatchers.statusDTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.lecture.PyrisLectureChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.util.IrisMessageFactory;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;

class IrisLectureChatMessageIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irislecturechatmessageintegration";

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisSessionRepository irisLectureChatSessionRepository;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    private Lecture lecture;

    private AtomicBoolean pipelineDone;

    private IrisLectureChatSession createSessionForUser(String userLogin) {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + userLogin);
        return irisLectureChatSessionRepository.save(new IrisLectureChatSession(lecture, user));
    }

    @BeforeEach
    void initTestCase() {
        List<User> users = userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
        for (User user : users) {
            user.setSelectedLLMUsageTimestamp(ZonedDateTime.now());
            user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
            userTestRepository.save(user);
        }

        Course course = courseUtilService.createCourse();
        lecture = lectureUtilService.createLecture(course, ZonedDateTime.now());

        activateIrisGlobally();
        activateIrisFor(course);

        pipelineDone = new AtomicBoolean(false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendOneMessage() throws Exception {
        var irisSession = createSessionForUser("student1");
        var messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        messageToSend.setMessageDifferentiator(1453);

        irisRequestMockProvider.mockLectureChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), null));

            pipelineDone.set(true);
        });
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);

        await().until(pipelineDone::get);

        var user = userTestRepository.findByIdElseThrow(irisSession.getUserId());
        verifyWebsocketActivityWasExactly(user.getLogin(), String.valueOf(irisSession.getId()), messageDTO(messageToSend.getContent()), statusDTO(IN_PROGRESS, NOT_STARTED),
                statusDTO(DONE, IN_PROGRESS), messageDTO("Hello World"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendOneMessageWithCustomInstructions() throws Exception {
        String testCustomInstructions = "Please focus on grammar.";
        var courseSettings = irisSettingsService.getRawIrisSettingsFor(lecture.getCourse());
        courseSettings.getIrisLectureChatSettings().setCustomInstructions(testCustomInstructions);
        irisSettingsService.saveIrisSettings(courseSettings);

        // Prepare session and message
        var irisSession = createSessionForUser("student1");
        var messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        messageToSend.setMessageDifferentiator(12345);

        // Mock Pyris response and assert customInstructions in DTO
        irisRequestMockProvider.mockLectureChatResponse(dto -> {
            assertThat(dto.customInstructions()).isEqualTo(testCustomInstructions);
            pipelineDone.set(true);
        });

        // Send message
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);

        // Await invocation
        await().until(pipelineDone::get);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendOneMessageToWrongSession() throws Exception {
        createSessionForUser("student1");
        var irisSession = createSessionForUser("student2");
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendMessageWithoutContent() throws Exception {
        var irisSession = createSessionForUser("student1");
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSession(irisSession);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendTwoMessages() throws Exception {
        var irisSession = createSessionForUser("student1");
        IrisMessage messageToSend1 = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);

        irisRequestMockProvider.mockLectureChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World 1", dto.initialStages(), null));

            pipelineDone.set(true);
        });

        irisRequestMockProvider.mockLectureChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World 2", dto.initialStages(), null));

            pipelineDone.set(true);
        });

        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend1, HttpStatus.CREATED);

        IrisMessage messageToSend2 = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend2, HttpStatus.CREATED);

        verify(websocketMessagingService, times(8)).sendMessageToUser(eq(TEST_PREFIX + "student1"), eq("/topic/iris/" + irisSession.getId()), any());

        var irisSessionFromDb = irisLectureChatSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId());
        assertThat(irisSessionFromDb.getMessages()).hasSize(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMessages() throws Exception {
        var irisSession = createSessionForUser("student1");

        IrisMessage message1 = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession), irisSession, IrisMessageSender.USER);
        IrisMessage message2 = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession), irisSession, IrisMessageSender.LLM);
        IrisMessage message3 = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession), irisSession, IrisMessageSender.USER);
        IrisMessage message4 = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession), irisSession, IrisMessageSender.LLM);

        var messages = request.getList("/api/iris/sessions/" + irisSession.getId() + "/messages", HttpStatus.OK, IrisMessage.class);
        assertThat(messages).hasSize(4).containsAll(List.of(message1, message2, message3, message4));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageHelpfulTrue() throws Exception {
        var irisSession = createSessionForUser("student1");
        IrisMessage message = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", true, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageHelpfulFalse() throws Exception {
        var irisSession = createSessionForUser("student1");
        IrisMessage message = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", false, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageHelpfulNull() throws Exception {
        var irisSession = createSessionForUser("student1");
        IrisMessage message = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", null, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageWrongSender() throws Exception {
        var irisSession = createSessionForUser("student1");
        IrisMessage message = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.USER);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", true, IrisMessage.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageWrongSession() throws Exception {
        IrisSession irisSession1 = createSessionForUser("student1");
        IrisSession irisSession2 = createSessionForUser("student2");
        IrisMessage message = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession1);
        IrisMessage irisMessage = irisMessageService.saveMessage(message, irisSession1, IrisMessageSender.USER);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession2.getId() + "/messages/" + irisMessage.getId() + "/helpful", true, IrisMessage.class, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resendMessage() throws Exception {
        IrisLectureChatSession irisSession = createSessionForUser("student1");
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);

        irisRequestMockProvider.mockLectureChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), null));

            pipelineDone.set(true);
        });

        var irisMessage = irisMessageService.saveMessage(messageToSend, irisSession, IrisMessageSender.USER);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/resend", null, HttpStatus.OK);
        await().until(() -> irisLectureChatSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId()).getMessages().size() == 2);
        var user = userTestRepository.findByIdElseThrow(irisSession.getUserId());
        verifyWebsocketActivityWasExactly(user.getLogin(), String.valueOf(irisSession.getId()), statusDTO(IN_PROGRESS, NOT_STARTED), statusDTO(DONE, IN_PROGRESS),
                messageDTO("Hello World"));
    }

    // User needs to be Admin to change settings
    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "ADMIN")
    void sendMessageRateLimitReached() throws Exception {
        var irisSession = createSessionForUser("student2");
        var messageToSend1 = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        var messageToSend2 = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);

        irisRequestMockProvider.mockLectureChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), null));

            pipelineDone.set(true);
        });

        var globalSettings = irisSettingsService.getGlobalSettings();
        globalSettings.getIrisProgrammingExerciseChatSettings().setRateLimit(1);
        globalSettings.getIrisProgrammingExerciseChatSettings().setRateLimitTimeframeHours(10);
        irisSettingsService.saveIrisSettings(globalSettings);

        try {
            request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend1, HttpStatus.CREATED);
            await().until(() -> irisLectureChatSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId()).getMessages().size() == 2);
            request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend2, HttpStatus.TOO_MANY_REQUESTS);
            var irisMessage = irisMessageService.saveMessage(messageToSend2, irisSession, IrisMessageSender.USER);
            request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/resend", null, HttpStatus.TOO_MANY_REQUESTS);

            var user = userTestRepository.findByIdElseThrow(irisSession.getUserId());
            verifyWebsocketActivityWasExactly(user.getLogin(), String.valueOf(irisSession.getId()), messageDTO(messageToSend1.getContent()), statusDTO(IN_PROGRESS, NOT_STARTED),
                    statusDTO(DONE, IN_PROGRESS), messageDTO("Hello World"));
        }
        finally {
            // Reset to not interfere with other tests
            globalSettings.getIrisProgrammingExerciseChatSettings().setRateLimit(null);
            globalSettings.getIrisProgrammingExerciseChatSettings().setRateLimitTimeframeHours(null);
            irisSettingsService.saveIrisSettings(globalSettings);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendMessageUpdatesSessionTitle() throws Exception {
        var irisSession = createSessionForUser("student1");
        var messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        final String expectedTitle = "New chat";

        irisRequestMockProvider.mockLectureChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), expectedTitle));

            pipelineDone.set(true);
        });

        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);
        await().until(pipelineDone::get);

        var irisSessionFromDb = irisLectureChatSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId());
        assertThat(irisSessionFromDb.getTitle()).isEqualTo(expectedTitle);
    }

    private void sendStatus(String jobId, String result, List<PyrisStageDTO> stages, String sessionTitle) throws Exception {
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobId))));
        request.postWithoutResponseBody("/api/iris/internal/pipelines/lecture-chat/runs/" + jobId + "/status", new PyrisLectureChatStatusUpdateDTO(result, stages, sessionTitle),
                HttpStatus.OK, headers);
    }
}
