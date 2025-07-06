package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.DONE;
import static de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.IN_PROGRESS;
import static de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.NOT_STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
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

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTextExerciseChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatWebsocketDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.util.IrisMessageFactory;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class IrisTextExerciseChatMessageIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iristextchatmessageintegration";

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisSessionRepository irisTextExerciseChatSessionRepository;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private TextExerciseUtilService exerciseUtilService;

    private TextExercise exercise;

    private AtomicBoolean pipelineDone;

    // TODO replace this with factory method
    private IrisTextExerciseChatSession createSessionForUser(String userLogin) {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + userLogin);
        return irisTextExerciseChatSessionRepository.save(new IrisTextExerciseChatSession(exercise, user));
    }

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);

        final Course course = exerciseUtilService.addCourseWithOneReleasedTextExercise("Test Exercise Title");
        exercise = (TextExercise) course.getExercises().iterator().next();
        exercise = textExerciseRepository.save(exercise);

        // Add a participation for student1.
        StudentParticipation studentParticipation = new StudentParticipation().exercise(exercise);
        studentParticipation.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        studentParticipationRepository.save(studentParticipation);

        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);
        pipelineDone = new AtomicBoolean(false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendOneMessage() throws Exception {
        var irisSession = createSessionForUser("student1");
        var messageToSend = createDefaultMockMessage(irisSession);
        messageToSend.setMessageDifferentiator(1453);

        irisRequestMockProvider.mockTextExerciseChatResponse(dto -> {
            assertThat(dto.execution().settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.execution().settings().authenticationToken(), "Hello World", dto.execution().initialStages()));

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
        // Set custom instructions for text exercise chat
        String testCustomInstructions = "Please focus on text formatting and grammar.";
        var exerciseSettings = irisSettingsService.getRawIrisSettingsFor(exercise);
        exerciseSettings.getIrisTextExerciseChatSettings().setCustomInstructions(testCustomInstructions);
        exerciseSettings.getIrisTextExerciseChatSettings().setEnabled(true);
        irisSettingsService.saveIrisSettings(exerciseSettings);

        // Prepare session and message
        var irisSession = createSessionForUser("student1");
        var messageToSend = createDefaultMockMessage(irisSession);
        messageToSend.setMessageDifferentiator(789101112);

        // Mock Pyris response and assert customInstructions in DTO
        irisRequestMockProvider.mockTextExerciseChatResponse(dto -> {
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
        IrisSession irisSession = createSessionForUser("student2");
        IrisMessage messageToSend = createDefaultMockMessage(irisSession);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendMessageWithoutContent() throws Exception {
        IrisSession irisSession = createSessionForUser("student1");
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSession(irisSession);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendTwoMessages() throws Exception {
        IrisSession irisSession = createSessionForUser("student1");
        IrisMessage messageToSend1 = createDefaultMockMessage(irisSession);

        irisRequestMockProvider.mockTextExerciseChatResponse(dto -> {
            assertThat(dto.execution().settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.execution().settings().authenticationToken(), "Hello World 1", dto.execution().initialStages()));

            pipelineDone.set(true);
        });

        irisRequestMockProvider.mockTextExerciseChatResponse(dto -> {
            assertThat(dto.execution().settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.execution().settings().authenticationToken(), "Hello World 2", dto.execution().initialStages()));

            pipelineDone.set(true);
        });

        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend1, HttpStatus.CREATED);

        IrisMessage messageToSend2 = createDefaultMockMessage(irisSession);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend2, HttpStatus.CREATED);

        verify(websocketMessagingService, times(8)).sendMessageToUser(eq(TEST_PREFIX + "student1"), eq("/topic/iris/" + irisSession.getId()), any());

        var irisSessionFromDb = irisTextExerciseChatSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId());
        assertThat(irisSessionFromDb.getMessages()).hasSize(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMessages() throws Exception {
        var irisSession = createSessionForUser("student1");

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
        IrisSession irisSession = createSessionForUser("student1");
        IrisMessage message = IrisMessageFactory.createIrisMessageForSession(irisSession);
        message.addContent(createMockTextContent());
        IrisMessage irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", true, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageHelpfulFalse() throws Exception {
        IrisSession irisSession = createSessionForUser("student1");
        IrisMessage message = IrisMessageFactory.createIrisMessageForSession(irisSession);
        message.addContent(createMockTextContent());
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", false, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageHelpfulNull() throws Exception {
        IrisSession irisSession = createSessionForUser("student1");
        IrisMessage message = IrisMessageFactory.createIrisMessageForSession(irisSession);
        message.addContent(createMockTextContent());
        IrisMessage irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", null, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageWrongSender() throws Exception {
        IrisSession irisSession = createSessionForUser("student1");
        IrisMessage message = IrisMessageFactory.createIrisMessageForSession(irisSession);
        message.addContent(createMockTextContent());
        IrisMessage irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.USER);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", true, IrisMessage.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageWrongSession() throws Exception {
        IrisSession irisSession1 = createSessionForUser("student1");
        IrisSession irisSession2 = createSessionForUser("student2");
        IrisMessage message = IrisMessageFactory.createIrisMessageForSession(irisSession1);
        message.addContent(createMockTextContent());
        IrisMessage irisMessage = irisMessageService.saveMessage(message, irisSession1, IrisMessageSender.USER);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession2.getId() + "/messages/" + irisMessage.getId() + "/helpful", true, IrisMessage.class, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resendMessage() throws Exception {
        var irisSession = createSessionForUser("student1");
        var messageToSend = createDefaultMockMessage(irisSession);

        irisRequestMockProvider.mockTextExerciseChatResponse(dto -> {
            assertThat(dto.execution().settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.execution().settings().authenticationToken(), "Hello World", dto.execution().initialStages()));

            pipelineDone.set(true);
        });

        var irisMessage = irisMessageService.saveMessage(messageToSend, irisSession, IrisMessageSender.USER);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/resend", null, HttpStatus.OK);
        await().until(() -> irisTextExerciseChatSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId()).getMessages().size() == 2);

        var user = userTestRepository.findByIdElseThrow(irisSession.getUserId());
        verifyWebsocketActivityWasExactly(user.getLogin(), String.valueOf(irisSession.getId()), statusDTO(IN_PROGRESS, NOT_STARTED), statusDTO(DONE, IN_PROGRESS),
                messageDTO("Hello World"));
    }

    // User needs to be Admin to change settings
    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "ADMIN")
    void sendMessageRateLimitReached() throws Exception {
        var irisSession = createSessionForUser("student2");
        var messageToSend1 = createDefaultMockMessage(irisSession);
        var messageToSend2 = createDefaultMockMessage(irisSession);

        irisRequestMockProvider.mockTextExerciseChatResponse(dto -> {
            assertThat(dto.execution().settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.execution().settings().authenticationToken(), "Hello World", dto.execution().initialStages()));

            pipelineDone.set(true);
        });

        var globalSettings = irisSettingsService.getGlobalSettings();
        globalSettings.getIrisProgrammingExerciseChatSettings().setRateLimit(1);
        globalSettings.getIrisProgrammingExerciseChatSettings().setRateLimitTimeframeHours(10);
        irisSettingsService.saveIrisSettings(globalSettings);

        try {
            request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend1, HttpStatus.CREATED);
            await().until(() -> irisTextExerciseChatSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId()).getMessages().size() == 2);
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

    private IrisMessage createDefaultMockMessage(IrisSession irisSession) {
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSession(irisSession);
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
                if (!(argument instanceof IrisChatWebsocketDTO websocketDTO)) {
                    return false;
                }
                if (websocketDTO.type() != IrisChatWebsocketDTO.IrisWebsocketMessageType.MESSAGE) {
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

    private ArgumentMatcher<Object> statusDTO(PyrisStageState... stageStates) {
        return new ArgumentMatcher<>() {

            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof IrisChatWebsocketDTO websocketDTO)) {
                    return false;
                }
                if (websocketDTO.type() != IrisChatWebsocketDTO.IrisWebsocketMessageType.STATUS) {
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
        request.postWithoutResponseBody("/api/iris/public/pyris/pipelines/text-exercise-chat/runs/" + jobId + "/status", new PyrisChatStatusUpdateDTO(result, stages, null, null),
                HttpStatus.OK, headers);
    }
}
