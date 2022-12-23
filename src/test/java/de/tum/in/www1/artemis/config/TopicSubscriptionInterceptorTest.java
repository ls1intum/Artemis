package de.tum.in.www1.artemis.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.security.Principal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.websocket.WebsocketConfiguration;

@SuppressWarnings("unchecked")
class TopicSubscriptionInterceptorTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "topicsubscriptioninterceptor";

    @Autowired
    private WebsocketConfiguration websocketConfiguration;

    @Test
    void testAllowSubscription() {
        database.addUsers(TEST_PREFIX, 4, 0, 1, 1);
        var course = database.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, false);
        var exercise = course.getExercises().stream().findFirst().orElseThrow();
        var participation = exercise.getStudentParticipations().stream().findFirst().orElseThrow();

        var exam = database.addExam(course);
        exam = database.addExerciseGroupsAndExercisesToExam(exam, false);
        var examExercise = exam.getExerciseGroups().get(0).getExercises().stream().findFirst().orElseThrow();

        var interceptor = websocketConfiguration.new TopicSubscriptionInterceptor();
        var msgMock = (Message<String>) mock(Message.class);
        try (var ignored = mockStatic(StompHeaderAccessor.class)) {
            var headerAccessorMock = mock(StompHeaderAccessor.class);
            when(StompHeaderAccessor.wrap(msgMock)).thenReturn(headerAccessorMock);
            when(headerAccessorMock.getCommand()).thenReturn(StompCommand.SUBSCRIBE);
            var principalMock = mock(Principal.class);
            when(headerAccessorMock.getUser()).thenReturn(principalMock);

            var channel = mock(MessageChannel.class);

            // Team Destination
            when(headerAccessorMock.getDestination()).thenReturn("/topic/participations/" + participation.getId() + "/team");

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student1");
            var returnedValue = interceptor.preSend(msgMock, channel);
            assertEquals(msgMock, returnedValue);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student2");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertNull(returnedValue);

            // Non Personal Exercise Result Destination
            when(headerAccessorMock.getDestination()).thenReturn("/topic/exercise/" + exercise.getId() + "/newResults");

            // Normal course exercise
            when(principalMock.getName()).thenReturn(TEST_PREFIX + "instructor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertEquals(msgMock, returnedValue);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "editor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertEquals(msgMock, returnedValue);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertNull(returnedValue);

            // Exam exercise
            when(headerAccessorMock.getDestination()).thenReturn("/topic/exercise/" + examExercise.getId() + "/newResults");

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "instructor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertEquals(msgMock, returnedValue);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "editor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertNull(returnedValue);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertNull(returnedValue);

            // Exam destination
            when(headerAccessorMock.getDestination()).thenReturn("/topic/exams/" + exam.getId() + "/test");

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "instructor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertEquals(msgMock, returnedValue);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "editor1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertNull(returnedValue);

            when(principalMock.getName()).thenReturn(TEST_PREFIX + "student1");
            returnedValue = interceptor.preSend(msgMock, channel);
            assertNull(returnedValue);
        }
    }
}
