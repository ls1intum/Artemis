package de.tum.in.www1.artemis.iris;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.message.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.message.IrisTextMessageContent;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.session.IrisChatSessionService;

class IrisSessionActivationIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irissessionactivationintegration";

    @Autowired
    private IrisChatSessionService irisChatSessionService;

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
        request.post("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void getCurrentSessionUnauthorized() throws Exception {
        request.get("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", HttpStatus.FORBIDDEN, IrisSession.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void createMessageUnauthorized() throws Exception {
        var irisSession = irisChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student3"));
        var messageToSend = irisSession.newMessage();
        messageToSend.addContent(createMockContent());
        request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void getMessagesUnauthorized() throws Exception {
        var irisSession = irisChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student4"));
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
}
