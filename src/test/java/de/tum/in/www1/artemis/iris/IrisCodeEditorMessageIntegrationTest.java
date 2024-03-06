package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.message.*;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.*;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.session.IrisCodeEditorSessionService;
import de.tum.in.www1.artemis.service.iris.websocket.IrisCodeEditorWebsocketService;
import de.tum.in.www1.artemis.util.IrisUtilTestService;
import de.tum.in.www1.artemis.util.LocalRepository;

class IrisCodeEditorMessageIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iriscodeeditormessageintegration";

    @Autowired
    private IrisCodeEditorSessionService irisCodeEditorSessionService;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private IrisExercisePlanStepRepository irisExercisePlanStepRepository;

    @Autowired
    private IrisUtilTestService irisUtilTestService;

    private ProgrammingExercise exercise;

    private LocalRepository repository;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 2, 0);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);
        repository = new LocalRepository("main");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testSendOneMessage() throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        var messageToSend = createDefaultMockMessage(irisSession);
        messageToSend.setMessageDifferentiator(1453);
        setupExercise();
        irisRequestMockProvider.mockMessageV2Response(Map.of("response", "Hi there!"));

        var irisMessage = request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.CREATED);
        assertThat(irisMessage.getSender()).isEqualTo(IrisMessageSender.USER);
        assertThat(irisMessage.getMessageDifferentiator()).isEqualTo(1453);
        assertThat(irisMessage.getContent().stream().map(IrisMessageContent::getContentAsString).toList())
                .isEqualTo(messageToSend.getContent().stream().map(IrisMessageContent::getContentAsString).toList());
        await().untilAsserted(() -> assertThat(irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId()).getMessages()).hasSize(2).contains(irisMessage));

        verifyWebsocketActivityWasExactly(irisSession, messageDTO(messageToSend.getContent()), messageDTO(List.of(new IrisTextMessageContent("Hi there!"))));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testSendOneMessageToWrongSession() throws Exception {
        var irisSession1 = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        var irisSession2 = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor2"));
        IrisMessage messageToSend = createDefaultMockMessage(irisSession1);
        request.postWithResponseBody("/api/iris/sessions/" + irisSession2.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testSendMessageWithoutContent() throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        var messageToSend = irisSession.newMessage();
        setupExercise();
        request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetMessages() throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));

        IrisMessage message1 = irisMessageService.saveMessage(createDefaultMockMessage(irisSession), irisSession, IrisMessageSender.USER);
        IrisMessage message2 = irisMessageService.saveMessage(createDefaultMockMessage(irisSession), irisSession, IrisMessageSender.LLM);
        IrisMessage message3 = irisMessageService.saveMessage(createDefaultMockMessage(irisSession), irisSession, IrisMessageSender.USER);
        IrisMessage message4 = irisMessageService.saveMessage(createDefaultMockMessage(irisSession), irisSession, IrisMessageSender.LLM);

        var messages = request.getList("/api/iris/sessions/" + irisSession.getId() + "/messages", HttpStatus.OK, IrisMessage.class);
        assertThat(messages).hasSize(4).containsAll(List.of(message1, message2, message3, message4));
    }

    private static Stream<Arguments> stepExecutions() {
        // @formatter:off
        var overwriteProblemStatement = Map.of("type", "overwrite", "updated", "New problem statement");
        var updateProblemStatement = Map.of("type", "modify", "changes", List.of(
                Map.of("from", "start", "to", "end", "updated", "updated content"))
        );
        var fileChanges = Map.of("changes", List.of(
                Map.of("type", "overwrite", "path", "overwrite_me.txt", "updated", "new content"),
                Map.of("type", "modify", "path", "modify_me.txt", "original", "quote", "updated", "updated quote"),
                Map.of("type", "create", "path", "create_me.txt", "content", "new file content"),
                Map.of("type", "delete", "path", "delete_me.txt"),
                Map.of("type", "rename", "path", "rename_me.txt", "updated", "renamed.txt")
        ));
        var noType = Map.of("!type", "...");
        var overwriteNoUpdated = Map.of("type", "overwrite");
        var modifyWithZeroChanges = Map.of("type", "modify", "changes", List.of());
        var modifyChangesNoFrom = Map.of("type", "modify", "changes", List.of(
                Map.of("to", "end", "updated", "updated content"))
        );
        var missingChanges = Map.of("type", "modify");
        var fileWithZeroChanges = Map.of("changes", List.of());
        return Stream.of(
                Arguments.of(ExerciseComponent.PROBLEM_STATEMENT, overwriteProblemStatement, IrisExercisePlanStep.ExecutionStage.COMPLETE),
                Arguments.of(ExerciseComponent.PROBLEM_STATEMENT, updateProblemStatement, IrisExercisePlanStep.ExecutionStage.COMPLETE),
                Arguments.of(ExerciseComponent.SOLUTION_REPOSITORY, fileChanges, IrisExercisePlanStep.ExecutionStage.COMPLETE),
                Arguments.of(ExerciseComponent.TEMPLATE_REPOSITORY, fileChanges, IrisExercisePlanStep.ExecutionStage.COMPLETE),
                Arguments.of(ExerciseComponent.TEST_REPOSITORY, fileChanges, IrisExercisePlanStep.ExecutionStage.COMPLETE),
                Arguments.of(ExerciseComponent.PROBLEM_STATEMENT, noType, IrisExercisePlanStep.ExecutionStage.FAILED),
                Arguments.of(ExerciseComponent.PROBLEM_STATEMENT, overwriteNoUpdated, IrisExercisePlanStep.ExecutionStage.FAILED),
                Arguments.of(ExerciseComponent.PROBLEM_STATEMENT, modifyWithZeroChanges, IrisExercisePlanStep.ExecutionStage.FAILED),
                Arguments.of(ExerciseComponent.PROBLEM_STATEMENT, modifyChangesNoFrom, IrisExercisePlanStep.ExecutionStage.FAILED),
                Arguments.of(ExerciseComponent.PROBLEM_STATEMENT, missingChanges, IrisExercisePlanStep.ExecutionStage.FAILED),
                Arguments.of(ExerciseComponent.SOLUTION_REPOSITORY, missingChanges, IrisExercisePlanStep.ExecutionStage.FAILED),
                Arguments.of(ExerciseComponent.TEMPLATE_REPOSITORY, missingChanges, IrisExercisePlanStep.ExecutionStage.FAILED),
                Arguments.of(ExerciseComponent.TEST_REPOSITORY, missingChanges, IrisExercisePlanStep.ExecutionStage.FAILED),
                Arguments.of(ExerciseComponent.SOLUTION_REPOSITORY, fileWithZeroChanges, IrisExercisePlanStep.ExecutionStage.FAILED),
                Arguments.of(ExerciseComponent.TEMPLATE_REPOSITORY, fileWithZeroChanges, IrisExercisePlanStep.ExecutionStage.FAILED),
                Arguments.of(ExerciseComponent.TEST_REPOSITORY, fileWithZeroChanges, IrisExercisePlanStep.ExecutionStage.FAILED)
        );
        // @formatter:on
    }

    @ParameterizedTest
    @MethodSource("stepExecutions")
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testExecutePlan(ExerciseComponent component, Map<String, ?> irisResponse, IrisExercisePlanStep.ExecutionStage expectedExecutionResult) throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        var message = irisSession.newMessage();
        var exercisePlan = new IrisExercisePlan();
        exercisePlan.setSteps(List.of(new IrisExercisePlanStep(component, "Make some changes")));
        message.addContent(exercisePlan);
        var savedMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        var savedPlan = (IrisExercisePlan) savedMessage.getContent().get(0);
        var savedStep = savedPlan.getSteps().get(0);
        setupExercise();

        irisRequestMockProvider.mockMessageV2Response(irisResponse);

        // This REST call does not return anything, but the changes will be sent over the websocket when they are ready
        request.postWithoutResponseBody("/api/iris/code-editor-sessions/" + irisSession.getId() + "/messages/" + savedMessage.getId() + "/contents/" + savedPlan.getId() + "/steps/"
                + savedStep.getId() + "/execute", null, HttpStatus.OK);

        switch (expectedExecutionResult) {
            case COMPLETE -> verifyWebsocketActivityWasExactly(irisSession, stepSuccessDTO(component));
            case FAILED -> verifyWebsocketActivityWasExactly(irisSession, stepFailedDTO());
        }
        assertThat(irisExercisePlanStepRepository.findByIdElseThrow(savedStep.getId()).getExecutionStage()).isEqualTo(expectedExecutionResult);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testExecutePlanResponseError() throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        var message = irisSession.newMessage();
        var exercisePlan = new IrisExercisePlan();
        exercisePlan.setSteps(List.of(new IrisExercisePlanStep(ExerciseComponent.PROBLEM_STATEMENT, "Make some changes")));
        message.addContent(exercisePlan);
        var savedMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        var savedPlan = (IrisExercisePlan) savedMessage.getContent().get(0);
        var savedStep = savedPlan.getSteps().get(0);
        setupExercise();

        irisRequestMockProvider.mockMessageV2Error(500);

        // This REST call does not return anything, but the changes will be sent over the websocket when they are ready
        request.postWithoutResponseBody("/api/iris/code-editor-sessions/" + irisSession.getId() + "/messages/" + savedMessage.getId() + "/contents/" + savedPlan.getId() + "/steps/"
                + savedStep.getId() + "/execute", null, HttpStatus.OK);

        verifyWebsocketActivityWasExactly(irisSession, stepFailedDTO());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateExercisePlanStep() throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        var message = irisSession.newMessage();
        message.addContent(createMockExercisePlanContent());
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        var content = irisMessage.getContent().get(0);
        setupExercise();
        assertThat(content).isInstanceOf(IrisExercisePlan.class);
        var step = ((IrisExercisePlan) content).getSteps().get(0);
        String updatedInstructions = "Updated instructions";
        step.setInstructions(updatedInstructions);

        var updatedStep = request.putWithResponseBody(
                "/api/iris/code-editor-sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/contents/" + content.getId() + "/steps/" + step.getId(), step,
                IrisExercisePlanStep.class, HttpStatus.OK);

        assertThat(updatedStep.getInstructions()).isEqualTo(updatedInstructions);
        assertThat(irisMessageRepository.findByIdElseThrow(irisMessage.getId()).getContent()).hasSize(1);
        assertThat(irisExercisePlanStepRepository.findByIdElseThrow(step.getId()).getInstructions()).isEqualTo(updatedInstructions);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void sendOneMessageBadRequest() throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        IrisMessage messageToSend = createDefaultMockMessage(irisSession);

        irisRequestMockProvider.mockMessageV2Error(500);
        setupExercise();

        request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.CREATED);

        verifyWebsocketActivityWasExactly(irisSession, messageDTO(messageToSend.getContent()), messageExceptionDTO());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void sendOneMessageEmptyBody() throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        IrisMessage messageToSend = createDefaultMockMessage(irisSession);

        irisRequestMockProvider.mockMessageV2Response(Map.of("invalid", "response"));
        setupExercise();

        request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.CREATED);

        verifyWebsocketActivityWasExactly(irisSession, messageDTO(messageToSend.getContent()), messageExceptionDTO());
    }

    private void setupExercise() throws Exception {
        var savedTemplateExercise = irisUtilTestService.setupTemplate(exercise, repository);
        var savedSolutionExercise = irisUtilTestService.setupSolution(savedTemplateExercise, repository);
        irisUtilTestService.setupTest(savedSolutionExercise, repository);
    }

    private IrisMessage createDefaultMockMessage(IrisSession irisSession) {
        var messageToSend = irisSession.newMessage();
        messageToSend.addContent(createMockTextContent());
        messageToSend.addContent(createMockExercisePlanContent());
        messageToSend.addContent(createMockTextContent());
        return messageToSend;
    }

    private IrisTextMessageContent createMockTextContent() {
        String[] adjectives = { "happy", "sad", "angry", "funny", "silly", "crazy", "beautiful", "smart" };
        String[] nouns = { "dog", "cat", "house", "car", "book", "computer", "phone", "shoe" };

        var rdm = ThreadLocalRandom.current();
        String randomAdjective = adjectives[rdm.nextInt(adjectives.length)];
        String randomNoun = nouns[rdm.nextInt(nouns.length)];

        var text = "The " + randomAdjective + " " + randomNoun + " jumped over the lazy dog.";
        return new IrisTextMessageContent(text);
    }

    private IrisExercisePlan createMockExercisePlanContent() {
        var content = new IrisExercisePlan();
        // @formatter:off
        content.setSteps(List.of(
                new IrisExercisePlanStep(ExerciseComponent.PROBLEM_STATEMENT, "I will edit the problem statement."),
                new IrisExercisePlanStep(ExerciseComponent.SOLUTION_REPOSITORY, "I will edit the solution repository."),
                new IrisExercisePlanStep(ExerciseComponent.TEMPLATE_REPOSITORY, "I will edit the template repository."),
                new IrisExercisePlanStep(ExerciseComponent.TEST_REPOSITORY, "I will edit the test repository.")
        ));
        // @formatter:on
        return content;
    }

    private ArgumentMatcher<Object> stepSuccessDTO(ExerciseComponent component) {
        return new ArgumentMatcher<>() {

            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof IrisCodeEditorWebsocketService.IrisWebsocketDTO websocketDTO)) {
                    return false;
                }
                if (websocketDTO.type() != IrisCodeEditorWebsocketService.IrisWebsocketMessageType.STEP_SUCCESS) {
                    return false;
                }
                // TODO: Could be improved by also checking that the file changes and updated problem statement are correct,
                // but this would require setting up the exercise with the correct files etc. to be successfully modified in the test
                return websocketDTO.stepExecutionSuccess().component() == component;
            }

            @Override
            public String toString() {
                return "IrisCodeEditorWebsocketService.IrisWebsocketDTO with type STEP_SUCCESS and component " + component;
            }
        };
    }

    private ArgumentMatcher<Object> stepFailedDTO() {
        return new ArgumentMatcher<>() {

            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof IrisCodeEditorWebsocketService.IrisWebsocketDTO websocketDTO)) {
                    return false;
                }
                return websocketDTO.type() == IrisCodeEditorWebsocketService.IrisWebsocketMessageType.STEP_EXCEPTION;
            }

            @Override
            public String toString() {
                return "IrisCodeEditorWebsocketService.IrisWebsocketDTO with type STEP_EXCEPTION";
            }
        };
    }

    private ArgumentMatcher<Object> messageDTO(List<IrisMessageContent> content) {
        return new ArgumentMatcher<>() {

            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof IrisCodeEditorWebsocketService.IrisWebsocketDTO websocketDTO)) {
                    return false;
                }
                if (websocketDTO.type() != IrisCodeEditorWebsocketService.IrisWebsocketMessageType.MESSAGE) {
                    return false;
                }
                return websocketDTO.message().getContent().stream().map(IrisMessageContent::getContentAsString).toList()
                        .equals(content.stream().map(IrisMessageContent::getContentAsString).toList());
            }

            @Override
            public String toString() {
                return "IrisCodeEditorWebsocketService.IrisWebsocketDTO with type MESSAGE and content " + content;
            }
        };
    }

    private ArgumentMatcher<Object> messageExceptionDTO() {
        return new ArgumentMatcher<>() {

            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof IrisCodeEditorWebsocketService.IrisWebsocketDTO websocketDTO)) {
                    return false;
                }
                return websocketDTO.type() == IrisCodeEditorWebsocketService.IrisWebsocketMessageType.ERROR;
            }

            @Override
            public String toString() {
                return "IrisCodeEditorWebsocketService.IrisWebsocketDTO with type EXCEPTION";
            }
        };
    }

}
