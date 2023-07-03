package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.websocket.team.ParticipationTeamWebsocketService;

class ParticipationTeamWebsocketServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "participationteamwebsocket";

    @Autowired
    private ParticipationTeamWebsocketService participationTeamWebsocketService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private StudentParticipation participation;

    private static String websocketTopic(Participation participation) {
        return "/topic/participations/" + participation.getId() + "/team";
    }

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 0);
        Course course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        ModelingExercise modelingExercise = exerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        participation = participationUtilService.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "student1");

        closeable = MockitoAnnotations.openMocks(this);
        participationTeamWebsocketService.clearDestinationTracker();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubscribeToParticipationTeamWebsocketTopic() {
        participationTeamWebsocketService.subscribe(participation.getId(), getStompHeaderAccessorMock("fakeSessionId"));
        verify(messagingTemplate, times(1)).convertAndSend(websocketTopic(participation), List.of());
        assertThat(participationTeamWebsocketService.getDestinationTracker()).as("Session was added to destination tracker.").hasSize(1);
        assertThat(participationTeamWebsocketService.getDestinationTracker()).as("Destination in tracker is correct.").containsValue(websocketTopic(participation));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTriggerSendOnlineTeamMembers() {
        participationTeamWebsocketService.triggerSendOnlineTeamStudents(participation.getId());
        verify(messagingTemplate, times(1)).convertAndSend(websocketTopic(participation), List.of());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUnsubscribeFromParticipationTeamWebsocketTopic() {
        StompHeaderAccessor stompHeaderAccessor1 = getStompHeaderAccessorMock("fakeSessionId1");
        StompHeaderAccessor stompHeaderAccessor2 = getStompHeaderAccessorMock("fakeSessionId2");

        participationTeamWebsocketService.subscribe(participation.getId(), stompHeaderAccessor1);
        participationTeamWebsocketService.subscribe(participation.getId(), stompHeaderAccessor2);
        participationTeamWebsocketService.unsubscribe(stompHeaderAccessor1.getSessionId());

        verify(messagingTemplate, times(3)).convertAndSend(websocketTopic(participation), List.of());
        assertThat(participationTeamWebsocketService.getDestinationTracker()).as("Session was removed from destination tracker.").hasSize(1);
        assertThat(participationTeamWebsocketService.getDestinationTracker()).as("Correct session was removed.").containsKey(stompHeaderAccessor2.getSessionId());
    }

    private StompHeaderAccessor getStompHeaderAccessorMock(String fakeSessionId) {
        StompHeaderAccessor stompHeaderAccessor = mock(StompHeaderAccessor.class, RETURNS_MOCKS);
        when(stompHeaderAccessor.getSessionId()).thenReturn(fakeSessionId);
        return stompHeaderAccessor;
    }
}
