package de.tum.cit.aet.artemis.iris;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.util.IrisChatSessionUtilService;
import de.tum.cit.aet.artemis.iris.util.IrisMessageFactory;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class IrisSessionActivationIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irisexercisesessionactivation";

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisChatSessionUtilService irisChatSessionUtilService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 4, 0, 0, 0);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        // The point of this test is to check the behavior of the exercise chat session when Iris is not enabled for an exercise
        // We need to actively disable it as it's on by default
        disableIrisFor(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createSessionUnauthorized() throws Exception {
        request.post(exerciseChatUrl(exercise.getId()), null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void getCurrentSessionUnauthorized() throws Exception {
        request.get(exerciseChatUrl(exercise.getId()), HttpStatus.FORBIDDEN, IrisSession.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void createMessageUnauthorized() throws Exception {
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student3"));
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSession(irisSession);
        messageToSend.addContent(createMockContent());
        request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void getMessagesUnauthorized() throws Exception {
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student4"));
        IrisMessage message1 = IrisMessageFactory.createIrisMessageForSession(irisSession);
        message1.addContent(createMockContent(), createMockContent(), createMockContent());
        IrisMessage message2 = IrisMessageFactory.createIrisMessageForSession(irisSession);
        message2.addContent(createMockContent(), createMockContent(), createMockContent());

        irisMessageService.saveMessage(message1, irisSession, IrisMessageSender.LLM);
        irisMessageService.saveMessage(message2, irisSession, IrisMessageSender.USER);

        request.getList("/api/iris/sessions/" + irisSession.getId() + "/messages", HttpStatus.FORBIDDEN, IrisMessage.class);
    }

    private IrisTextMessageContent createMockContent() {
        return new IrisTextMessageContent("Not relevant for the test cases");
    }

    private static String exerciseChatUrl(long sessionId) {
        return "/api/iris/programming-exercise-chat/" + sessionId + "/sessions";
    }
}
