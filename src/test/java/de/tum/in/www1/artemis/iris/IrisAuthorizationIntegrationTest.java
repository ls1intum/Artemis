package de.tum.in.www1.artemis.iris;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisMessageContent;
import de.tum.in.www1.artemis.domain.iris.IrisMessageSender;
import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;

class IrisAuthorizationIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "irisauthorizationintegration";

    @Autowired
    private IrisSessionService irisSessionService;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, 4, 0, 0, 0);

        final Course course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise.setIrisActivated(false);
        programmingExerciseRepository.save(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createSessionUnauthorized() throws Exception {
        request.post("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void getCurrentSessionUnauthorized() throws Exception {
        request.get("/api/iris/programming-exercises/" + exercise.getId() + "/sessions", HttpStatus.BAD_REQUEST, IrisSession.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void createMessageUnauthorized() throws Exception {
        var irisSession = irisSessionService.createSessionForProgrammingExercise(exercise, database.getUserByLogin(TEST_PREFIX + "student3"));
        var messageToSend = new IrisMessage();
        messageToSend.setSession(irisSession);
        messageToSend.setSentAt(ZonedDateTime.now());
        messageToSend.setContent(List.of(createMockContent(messageToSend)));
        request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void getMessagesUnauthorized() throws Exception {
        var irisSession = irisSessionService.createSessionForProgrammingExercise(exercise, database.getUserByLogin(TEST_PREFIX + "student4"));
        var message1 = new IrisMessage();
        message1.setSession(irisSession);
        message1.setSentAt(ZonedDateTime.now());
        message1.setContent(List.of(createMockContent(message1), createMockContent(message1), createMockContent(message1)));
        var message2 = new IrisMessage();
        message2.setSession(irisSession);
        message2.setSentAt(ZonedDateTime.now());
        message2.setContent(List.of(createMockContent(message2), createMockContent(message2), createMockContent(message2)));
        var message3 = new IrisMessage();
        message3.setSession(irisSession);
        message3.setSentAt(ZonedDateTime.now());
        message3.setContent(List.of(createMockContent(message3), createMockContent(message3), createMockContent(message3)));

        irisMessageService.saveMessage(message1, irisSession, IrisMessageSender.ARTEMIS);
        irisMessageService.saveMessage(message2, irisSession, IrisMessageSender.LLM);
        irisMessageService.saveMessage(message3, irisSession, IrisMessageSender.USER);

        request.getList("/api/iris/sessions/" + irisSession.getId() + "/messages", HttpStatus.BAD_REQUEST, IrisMessage.class);
    }

    private IrisMessageContent createMockContent(IrisMessage message) {
        var content = new IrisMessageContent();
        var rdm = ThreadLocalRandom.current();
        content.setId(rdm.nextLong());
        content.setMessage(message);

        content.setTextContent("Not relevant for the test cases");
        return content;
    }
}
