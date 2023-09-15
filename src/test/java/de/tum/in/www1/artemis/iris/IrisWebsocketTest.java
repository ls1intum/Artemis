package de.tum.in.www1.artemis.iris;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisMessageContent;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.IrisWebsocketService;

@ActiveProfiles("iris")
class IrisWebsocketTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iriswebsocketintegration";

    @Autowired
    private IrisWebsocketService irisWebsocketService;

    @Autowired
    private IrisSessionService irisSessionService;

    @Autowired
    private WebsocketMessagingService websocketMessagingService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendMessage() {
        var irisSession = irisSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var message = new IrisMessage();
        message.setSession(irisSession);
        message.setSentAt(ZonedDateTime.now());
        message.setContent(List.of(createMockContent(message), createMockContent(message)));
        message.setMessageDifferentiator(101010);
        irisWebsocketService.sendMessage(message);
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(TEST_PREFIX + "student1"), eq("/topic/iris/sessions/" + irisSession.getId()),
                eq(new IrisWebsocketService.IrisWebsocketDTO(message)));
    }

    private IrisMessageContent createMockContent(IrisMessage message) {
        var content = new IrisMessageContent();
        var rdm = ThreadLocalRandom.current();
        content.setId(rdm.nextLong());
        content.setMessage(message);
        String[] adjectives = { "happy", "sad", "angry", "funny", "silly", "crazy", "beautiful", "smart" };
        String[] nouns = { "dog", "cat", "house", "car", "book", "computer", "phone", "shoe" };

        String randomAdjective = adjectives[rdm.nextInt(adjectives.length)];
        String randomNoun = nouns[rdm.nextInt(nouns.length)];

        content.setTextContent("The " + randomAdjective + " " + randomNoun + " jumped over the lazy dog.");
        return content;
    }
}
