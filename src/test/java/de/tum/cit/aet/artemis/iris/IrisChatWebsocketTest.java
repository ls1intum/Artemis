package de.tum.cit.aet.artemis.iris;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.iris.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.service.iris.IrisRateLimitService;
import de.tum.cit.aet.artemis.service.iris.dto.IrisChatWebsocketDTO;
import de.tum.cit.aet.artemis.service.iris.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.service.iris.websocket.IrisChatWebsocketService;

@ActiveProfiles("iris")
class IrisChatWebsocketTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irischatwebsocketintegration";

    @Autowired
    private IrisChatWebsocketService irisChatWebsocketService;

    @Autowired
    private IrisExerciseChatSessionService irisExerciseChatSessionService;

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
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var message = irisSession.newMessage();
        message.addContent(createMockContent(), createMockContent());
        message.setMessageDifferentiator(101010);
        irisChatWebsocketService.sendMessage(irisSession, message, List.of());
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(TEST_PREFIX + "student1"), eq("/topic/iris/" + irisSession.getId()),
                eq(new IrisChatWebsocketDTO(message, new IrisRateLimitService.IrisRateLimitInformation(0, -1, 0), List.of(), List.of())));
    }

    private IrisTextMessageContent createMockContent() {
        String[] adjectives = { "happy", "sad", "angry", "funny", "silly", "crazy", "beautiful", "smart" };
        String[] nouns = { "dog", "cat", "house", "car", "book", "computer", "phone", "shoe" };

        var rdm = ThreadLocalRandom.current();
        String randomAdjective = adjectives[rdm.nextInt(adjectives.length)];
        String randomNoun = nouns[rdm.nextInt(nouns.length)];
        var text = "The " + randomAdjective + " " + randomNoun + " jumped over the lazy dog.";

        var content = new IrisTextMessageContent(text);
        content.setId(rdm.nextLong());
        return content;
    }
}
