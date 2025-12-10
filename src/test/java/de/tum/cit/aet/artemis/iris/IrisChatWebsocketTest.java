package de.tum.cit.aet.artemis.iris;

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

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatWebsocketDTO;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.iris.util.IrisChatSessionUtilService;
import de.tum.cit.aet.artemis.iris.util.IrisMessageFactory;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class IrisChatWebsocketTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irischatwebsocketintegration";

    @Autowired
    private IrisChatWebsocketService irisChatWebsocketService;

    @Autowired
    private WebsocketMessagingService websocketMessagingService;

    @Autowired
    private IrisChatSessionUtilService irisChatSessionUtilService;

    @Autowired
    private IrisRateLimitService irisRateLimitService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        List<User> users = userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        for (User user : users) {
            user.setSelectedLLMUsageTimestamp(ZonedDateTime.parse("2025-12-11T00:00:00Z"));
            user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
            userTestRepository.save(user);
        }

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendMessage() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise, user);
        IrisMessage message = IrisMessageFactory.createIrisMessageForSession(irisSession);
        message.addContent(createMockContent(), createMockContent());
        message.setMessageDifferentiator(101010);
        irisChatWebsocketService.sendMessage(irisSession, message, List.of());

        var expectedRateLimitInfo = irisRateLimitService.getRateLimitInformation(irisSession, user);
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(TEST_PREFIX + "student1"), eq("/topic/iris/" + irisSession.getId()),
                eq(new IrisChatWebsocketDTO(message, expectedRateLimitInfo, List.of(), null, null, null)));
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
