package de.tum.cit.aet.artemis.iris;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class IrisSessionActivationIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irisexercisesessionactivation";

    @Autowired
    private IrisExerciseChatSessionService irisExerciseChatSessionService;

    @Autowired
    private IrisMessageService irisMessageService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 4, 0, 0, 0);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
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
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student3"));
        var messageToSend = irisSession.newMessage();
        messageToSend.addContent(createMockContent());
        request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void getMessagesUnauthorized() throws Exception {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student4"));
        var message1 = irisSession.newMessage();
        message1.addContent(createMockContent(), createMockContent(), createMockContent());
        var message2 = irisSession.newMessage();
        message2.addContent(createMockContent(), createMockContent(), createMockContent());

        irisMessageService.saveMessage(message1, irisSession, IrisMessageSender.LLM);
        irisMessageService.saveMessage(message2, irisSession, IrisMessageSender.USER);

        request.getList("/api/iris/sessions/" + irisSession.getId() + "/messages", HttpStatus.FORBIDDEN, IrisMessage.class);
    }

    private IrisTextMessageContent createMockContent() {
        var content = new IrisTextMessageContent("Not relevant for the test cases");
        content.setId(ThreadLocalRandom.current().nextLong());
        return content;
    }

    private static String exerciseChatUrl(long sessionId) {
        return "/api/iris/exercise-chat/" + sessionId + "/sessions";
    }
}
