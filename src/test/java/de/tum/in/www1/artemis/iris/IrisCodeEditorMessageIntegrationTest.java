package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.message.*;
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.*;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.session.IrisCodeEditorSessionService;
import de.tum.in.www1.artemis.util.IrisUtilTestService;
import de.tum.in.www1.artemis.util.LocalRepository;

class IrisCodeEditorMessageIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iriscodeeditormessageintegration";

    @Autowired
    private IrisSessionService irisSessionService;

    @Autowired
    private IrisCodeEditorSessionService irisCodeEditorSessionService;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private IrisExercisePlanComponentRepository irisExercisePlanComponentRepository;

    @Autowired
    private IrisUtilTestService irisUtilTestService;

    private ProgrammingExercise exercise;

    private LocalRepository repository;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 2, 0);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisFor(course);
        activateIrisFor(exercise);
        repository = new LocalRepository("main");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void sendOneMessage() throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        var messageToSend = createDefaultMockMessage(irisSession);
        messageToSend.setMessageDifferentiator(1453);
        setupExercise();
        irisRequestMockProvider.mockMessageResponse("Hello World");

        var irisMessage = request.postWithResponseBody("/api/iris/code-editor-sessions/" + irisSession.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.CREATED);
        assertThat(irisSession instanceof IrisCodeEditorSession).isEqualTo(true);
        assertThat(irisMessage.getSender()).isEqualTo(IrisMessageSender.USER);
        assertThat(irisMessage.getMessageDifferentiator()).isEqualTo(1453);
        // Compare contents of messages by only comparing the textContent field
        assertThat(irisMessage.getContent()).hasSize(3).map(IrisMessageContent::getContentAsString)
                .isEqualTo(messageToSend.getContent().stream().map(IrisMessageContent::getContentAsString).toList());
        await().untilAsserted(() -> assertThat(irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId()).getMessages()).hasSize(2).contains(irisMessage));

        // TODO: update AbstractIrisIntegrationTest.java later
        // verifyMessageWasSentOverWebsocket(TEST_PREFIX + "editor1", irisSession.getId(), messageToSend);
        // verifyMessageWasSentOverWebsocket(TEST_PREFIX + "editor1", irisSession.getId(), "Hello World");
        // verifyNothingElseWasSentOverWebsocket(TEST_PREFIX + "editor1", irisSession.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void sendOneMessageToWrongSession() throws Exception {
        var irisSession1 = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        var irisSession2 = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor2"));
        IrisMessage messageToSend = createDefaultMockMessage(irisSession1);
        request.postWithResponseBody("/api/iris/code-editor-sessions/" + irisSession2.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void sendMessageWithoutContent() throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        var messageToSend = new IrisMessage();
        messageToSend.setSession(irisSession);
        request.postWithResponseBody("/api/iris/code-editor-sessions/" + irisSession.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getMessages() throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        IrisMessage message1 = createDefaultMockMessage(irisSession);
        IrisMessage message2 = createDefaultMockMessage(irisSession);
        IrisMessage message3 = createDefaultMockMessage(irisSession);
        IrisMessage message4 = createDefaultMockMessage(irisSession);

        irisMessageService.saveMessage(message1, irisSession, IrisMessageSender.ARTEMIS);
        message2 = irisMessageService.saveMessage(message2, irisSession, IrisMessageSender.LLM);
        message3 = irisMessageService.saveMessage(message3, irisSession, IrisMessageSender.USER);
        message4 = irisMessageService.saveMessage(message4, irisSession, IrisMessageSender.LLM);

        var messages = request.getList("/api/iris/code-editor-sessions/" + irisSession.getId() + "/messages", HttpStatus.OK, IrisMessage.class);
        assertThat(messages).hasSize(3).usingElementComparator((o1, o2) -> {
            return o1.getContent().size() == o2.getContent().size() && o1.getContent().stream().map(IrisMessageContent::getContentAsString).toList()
                    .equals(o2.getContent().stream().map(IrisMessageContent::getContentAsString).toList()) ? 0 : -1;
        }).isEqualTo(List.of(message2, message3, message4));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void executePlan() throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        var message = new IrisMessage();
        message.setSession(irisSession);
        message.addContent(createMockExercisePlanContent(message));
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        var exercisePlanContent = irisMessage.getContent().get(0);
        setupExercise();

        request.postWithResponseBody(
                "/api/iris/code-editor-sessions/" + irisSession.getId() + "/messages" + irisMessage.getId() + "/contents/" + exercisePlanContent.getId() + "/execute", null,
                Void.class, HttpStatus.OK);

        // TODO: wait for requestExerciseChanges() complete
        assertThat(irisMessage.getSender()).isEqualTo(IrisMessageSender.LLM);
        await().untilAsserted(() -> assertThat(irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId()).getMessages()).hasSize(1).contains(irisMessage));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void updateComponentPlan() throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        var message = new IrisMessage();
        message.setSession(irisSession);
        message.addContent(createMockExercisePlanContent(message));
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        var exercisePlanContent = irisMessage.getContent().get(0);
        setupExercise();
        assertThat(exercisePlanContent instanceof IrisExercisePlanMessageContent).isEqualTo(true);
        var component = ((IrisExercisePlanMessageContent) exercisePlanContent).getSteps().get(0);

        request.putWithResponseBody("/api/iris/code-editor-sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/contents/" + exercisePlanContent.getId()
                + "/components/" + component.getId(), updateProblemStatement(component), IrisExercisePlanStep.class, HttpStatus.OK);
        var irisMessageFromDB = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        var componentFromDB = irisExercisePlanComponentRepository.findByIdElseThrow(component.getId());
        assertThat(irisMessageFromDB.getContent()).hasSize(1);
        assertThat(componentFromDB.getInstructions()).isEqualTo("test PS");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void sendOneMessageBadRequest() throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        IrisMessage messageToSend = createDefaultMockMessage(irisSession);

        irisRequestMockProvider.mockMessageError();
        setupExercise();

        request.postWithResponseBody("/api/iris/code-editor-sessions/" + irisSession.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.CREATED);

        // TODO: update AbstractIrisIntegrationTest.java later
        // verifyMessageWasSentOverWebsocket(TEST_PREFIX + "editor1", irisSession.getId(), messageToSend);
        // verifyErrorWasSentOverWebsocket(TEST_PREFIX + "editor1", irisSession.getId());
        // verifyNothingElseWasSentOverWebsocket(TEST_PREFIX + "editor1", irisSession.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void sendOneMessageEmptyBody() throws Exception {
        var irisSession = irisCodeEditorSessionService.createSession(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        IrisMessage messageToSend = createDefaultMockMessage(irisSession);

        irisRequestMockProvider.mockMessageResponse(null);
        setupExercise();

        request.postWithResponseBody("/api/iris/code-editor-sessions/" + irisSession.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.CREATED);

        // TODO: update AbstractIrisIntegrationTest.java later
        // verifyMessageWasSentOverWebsocket(TEST_PREFIX + "editor1", irisSession.getId(), messageToSend);
        // verifyErrorWasSentOverWebsocket(TEST_PREFIX + "editor1", irisSession.getId());
        // verifyNothingElseWasSentOverWebsocket(TEST_PREFIX + "editor1", irisSession.getId());
    }

    private void setupExercise() throws Exception {
        var savedTemplateExercise = irisUtilTestService.setupTemplate(exercise, repository);
        var savedSolutionExercise = irisUtilTestService.setupSolution(savedTemplateExercise, repository);
        var savedExercise = irisUtilTestService.setupTest(savedSolutionExercise, repository);
        activateIrisFor(savedExercise);
    }

    private IrisMessage createDefaultMockMessage(IrisSession irisSession) {
        var messageToSend = new IrisMessage();
        messageToSend.setSession(irisSession);
        messageToSend.addContent(createMockTextContent(messageToSend));
        messageToSend.addContent(createMockTextContent(messageToSend));
        messageToSend.addContent(createMockTextContent(messageToSend));
        return messageToSend;
    }

    private IrisTextMessageContent createMockTextContent(IrisMessage message) {
        String[] adjectives = { "happy", "sad", "angry", "funny", "silly", "crazy", "beautiful", "smart" };
        String[] nouns = { "dog", "cat", "house", "car", "book", "computer", "phone", "shoe" };

        var rdm = ThreadLocalRandom.current();
        String randomAdjective = adjectives[rdm.nextInt(adjectives.length)];
        String randomNoun = nouns[rdm.nextInt(nouns.length)];

        var text = "The " + randomAdjective + " " + randomNoun + " jumped over the lazy dog.";
        var content = new IrisTextMessageContent(message, text);
        content.setId(rdm.nextLong());
        return content;
    }

    private IrisExercisePlanMessageContent createMockExercisePlanContent(IrisMessage message) {
        var content = new IrisExercisePlanMessageContent();
        content.setSteps(List.of(new IrisExercisePlanStep(content, ExerciseComponent.PROBLEM_STATEMENT, "I will edit the problem statement."),
                new IrisExercisePlanStep(content, ExerciseComponent.SOLUTION_REPOSITORY, "I will edit the solution repository."),
                new IrisExercisePlanStep(content, ExerciseComponent.TEMPLATE_REPOSITORY, "I will edit the template repository."),
                new IrisExercisePlanStep(content, ExerciseComponent.TEST_REPOSITORY, "I will edit the test repository.")));
        content.setId(ThreadLocalRandom.current().nextLong());
        content.setMessage(message);
        return content;
    }

    private IrisExercisePlanStep updateProblemStatement(IrisExercisePlanStep psComponent) {
        psComponent.setInstructions("test PS");
        return psComponent;
    }

}
