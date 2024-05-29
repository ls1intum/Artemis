package de.tum.in.www1.artemis.iris;

import static de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageStateDTO.DONE;
import static de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageStateDTO.IN_PROGRESS;
import static de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageStateDTO.NOT_STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageContent;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.message.IrisTextMessageContent;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.iris.IrisMessageRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageStateDTO;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.session.IrisExerciseChatSessionService;
import de.tum.in.www1.artemis.service.iris.websocket.IrisWebsocketDTO;
import de.tum.in.www1.artemis.util.IrisUtilTestService;
import de.tum.in.www1.artemis.util.LocalRepository;

class IrisChatMessageIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irismessageintegration";

    @Autowired
    private IrisExerciseChatSessionService irisExerciseChatSessionService;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private IrisUtilTestService irisUtilTestService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private ProgrammingExercise exercise;

    private LocalRepository repository;

    private AtomicBoolean pipelineDone;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);
        repository = new LocalRepository("main");
        pipelineDone = new AtomicBoolean(false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendOneMessage() throws Exception {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var messageToSend = createDefaultMockMessage(irisSession);
        messageToSend.setMessageDifferentiator(1453);

        setupExercise();

        /*
         * irisRequestMockProvider.mockRunResponse(dto -> {
         * assertThat(dto.settings().authenticationToken()).isNotNull();
         * assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages()));
         * pipelineDone.set(true);
         * });
         */
        fail("This test is not yet implemented. Implement it and remove the fail call.");

        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);

        await().until(pipelineDone::get);

        verifyWebsocketActivityWasExactly(irisSession, messageDTO(messageToSend.getContent()), statusDTO(IN_PROGRESS, NOT_STARTED), statusDTO(DONE, IN_PROGRESS),
                messageDTO("Hello World"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendOneMessageToWrongSession() throws Exception {
        irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        IrisMessage messageToSend = createDefaultMockMessage(irisSession);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendMessageWithoutContent() throws Exception {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var messageToSend = irisSession.newMessage();
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendTwoMessages() throws Exception {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        IrisMessage messageToSend1 = createDefaultMockMessage(irisSession);

        setupExercise();

        /*
         * irisRequestMockProvider.mockRunResponse(dto -> {
         * assertThat(dto.settings().authenticationToken()).isNotNull();
         * assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World 1", dto.initialStages()));
         * pipelineDone.set(true);
         * });
         * irisRequestMockProvider.mockRunResponse(dto -> {
         * assertThat(dto.settings().authenticationToken()).isNotNull();
         * assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World 2", dto.initialStages()));
         * pipelineDone.set(true);
         * });
         */
        fail("This test is not yet implemented. Implement it and remove the fail call.");

        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend1, HttpStatus.CREATED);

        IrisMessage messageToSend2 = createDefaultMockMessage(irisSession);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend2, HttpStatus.CREATED);

        verify(websocketMessagingService, times(8)).sendMessageToUser(eq(TEST_PREFIX + "student1"), eq("/topic/iris/" + irisSession.getId()), any());

        var irisSessionFromDb = irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId());
        assertThat(irisSessionFromDb.getMessages()).hasSize(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMessages() throws Exception {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        IrisMessage message1 = irisMessageService.saveMessage(createDefaultMockMessage(irisSession), irisSession, IrisMessageSender.USER);
        IrisMessage message2 = irisMessageService.saveMessage(createDefaultMockMessage(irisSession), irisSession, IrisMessageSender.LLM);
        IrisMessage message3 = irisMessageService.saveMessage(createDefaultMockMessage(irisSession), irisSession, IrisMessageSender.USER);
        IrisMessage message4 = irisMessageService.saveMessage(createDefaultMockMessage(irisSession), irisSession, IrisMessageSender.LLM);

        var messages = request.getList("/api/iris/sessions/" + irisSession.getId() + "/messages", HttpStatus.OK, IrisMessage.class);
        assertThat(messages).hasSize(4).containsAll(List.of(message1, message2, message3, message4));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageHelpfulTrue() throws Exception {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var message = irisSession.newMessage();
        message.addContent(createMockTextContent());
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", true, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageHelpfulFalse() throws Exception {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var message = irisSession.newMessage();
        message.addContent(createMockTextContent());
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", false, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageHelpfulNull() throws Exception {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var message = irisSession.newMessage();
        message.addContent(createMockTextContent());
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", null, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageWrongSender() throws Exception {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var message = irisSession.newMessage();
        message.addContent(createMockTextContent());
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.USER);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", true, IrisMessage.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageWrongSession() throws Exception {
        var irisSession1 = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var irisSession2 = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        var message = irisSession1.newMessage();
        message.addContent(createMockTextContent());
        var irisMessage = irisMessageService.saveMessage(message, irisSession1, IrisMessageSender.USER);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession2.getId() + "/messages/" + irisMessage.getId() + "/helpful", true, IrisMessage.class, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resendMessage() throws Exception {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var messageToSend = createDefaultMockMessage(irisSession);

        setupExercise();

        /*
         * irisRequestMockProvider.mockRunResponse(dto -> {
         * assertThat(dto.settings().authenticationToken()).isNotNull();
         * assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages()));
         * pipelineDone.set(true);
         * });
         */
        fail("This test is not yet implemented. Implement it and remove the fail call.");

        var irisMessage = irisMessageService.saveMessage(messageToSend, irisSession, IrisMessageSender.USER);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/resend", null, HttpStatus.OK);
        await().until(() -> irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId()).getMessages().size() == 2);
        verifyWebsocketActivityWasExactly(irisSession, statusDTO(IN_PROGRESS, NOT_STARTED), statusDTO(DONE, IN_PROGRESS), messageDTO("Hello World"));
    }

    // User needs to be Admin to change settings
    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "ADMIN")
    void sendMessageRateLimitReached() throws Exception {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        var messageToSend1 = createDefaultMockMessage(irisSession);
        var messageToSend2 = createDefaultMockMessage(irisSession);

        setupExercise();

        /*
         * irisRequestMockProvider.mockRunResponse(dto -> {
         * assertThat(dto.settings().authenticationToken()).isNotNull();
         * assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages()));
         * pipelineDone.set(true);
         * });
         */
        fail("This test is not yet implemented. Implement it and remove the fail call.");

        var globalSettings = irisSettingsService.getGlobalSettings();
        globalSettings.getIrisChatSettings().setRateLimit(1);
        globalSettings.getIrisChatSettings().setRateLimitTimeframeHours(10);
        irisSettingsService.saveIrisSettings(globalSettings);

        try {
            request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend1, HttpStatus.CREATED);
            await().until(() -> irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId()).getMessages().size() == 2);
            request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend2, HttpStatus.TOO_MANY_REQUESTS);
            var irisMessage = irisMessageService.saveMessage(messageToSend2, irisSession, IrisMessageSender.USER);
            request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/resend", null, HttpStatus.TOO_MANY_REQUESTS);

            verifyWebsocketActivityWasExactly(irisSession, messageDTO(messageToSend1.getContent()), statusDTO(IN_PROGRESS, NOT_STARTED), statusDTO(DONE, IN_PROGRESS),
                    messageDTO("Hello World"));
        }
        finally {
            // Reset to not interfere with other tests
            globalSettings.getIrisChatSettings().setRateLimit(null);
            globalSettings.getIrisChatSettings().setRateLimitTimeframeHours(null);
            irisSettingsService.saveIrisSettings(globalSettings);
        }
    }

    private void setupExercise() throws Exception {
        var savedExercise = irisUtilTestService.setupTemplate(exercise, repository);
        savedExercise = irisUtilTestService.setupSolution(savedExercise, repository);
        savedExercise = irisUtilTestService.setupTest(savedExercise, repository);
        var exerciseParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(savedExercise, TEST_PREFIX + "student1");
        irisUtilTestService.setupStudentParticipation(exerciseParticipation, repository);
    }

    private IrisMessage createDefaultMockMessage(IrisSession irisSession) {
        var messageToSend = irisSession.newMessage();
        messageToSend.addContent(createMockTextContent(), createMockTextContent(), createMockTextContent());
        return messageToSend;
    }

    private IrisMessageContent createMockTextContent() {
        String[] adjectives = { "happy", "sad", "angry", "funny", "silly", "crazy", "beautiful", "smart" };
        String[] nouns = { "dog", "cat", "house", "car", "book", "computer", "phone", "shoe" };

        var rdm = ThreadLocalRandom.current();
        String randomAdjective = adjectives[rdm.nextInt(adjectives.length)];
        String randomNoun = nouns[rdm.nextInt(nouns.length)];

        var text = "The " + randomAdjective + " " + randomNoun + " jumped over the lazy dog.";
        return new IrisTextMessageContent(text);
    }

    private ArgumentMatcher<Object> messageDTO(String message) {
        return messageDTO(List.of(new IrisTextMessageContent(message)));
    }

    private ArgumentMatcher<Object> messageDTO(List<IrisMessageContent> content) {
        return new ArgumentMatcher<>() {

            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof IrisWebsocketDTO websocketDTO)) {
                    return false;
                }
                if (websocketDTO.type() != IrisWebsocketDTO.IrisWebsocketMessageType.MESSAGE) {
                    return false;
                }
                return Objects.equals(websocketDTO.message().getContent().stream().map(IrisMessageContent::getContentAsString).toList(),
                        content.stream().map(IrisMessageContent::getContentAsString).toList());
            }

            @Override
            public String toString() {
                return "IrisChatWebsocketService.IrisWebsocketDTO with type MESSAGE and content " + content;
            }
        };
    }

    private ArgumentMatcher<Object> statusDTO(PyrisStageStateDTO... stageStates) {
        return new ArgumentMatcher<>() {

            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof IrisWebsocketDTO websocketDTO)) {
                    return false;
                }
                if (websocketDTO.type() != IrisWebsocketDTO.IrisWebsocketMessageType.STATUS) {
                    return false;
                }
                if (websocketDTO.stages() == null) {
                    return stageStates == null;
                }
                if (websocketDTO.stages().size() != stageStates.length) {
                    return false;
                }
                return websocketDTO.stages().stream().map(PyrisStageDTO::state).toList().equals(List.of(stageStates));
            }

            @Override
            public String toString() {
                return "IrisChatWebsocketService.IrisWebsocketDTO with type STATUS and stage states " + Arrays.toString(stageStates);
            }
        };
    }

    private void sendStatus(String jobId, String result, List<PyrisStageDTO> stages) throws Exception {
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobId))));
        request.postWithoutResponseBody("/api/public/pyris/pipelines/exercise-chat/runs/" + jobId + "/status", new PyrisChatStatusUpdateDTO(result, stages), HttpStatus.OK,
                headers);
    }
}
