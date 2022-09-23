package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

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
import de.tum.in.www1.artemis.web.websocket.team.ParticipationTeamWebsocketService;

class ParticipationTeamWebsocketServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ParticipationTeamWebsocketService participationTeamWebsocketService;

    private StudentParticipation participation;

    private static String websocketTopic(Participation participation) {
        return "/topic/participations/" + participation.getId() + "/team";
    }

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        database.addUsers(3, 0, 0, 0);
        Course course = database.addCourseWithOneModelingExercise();
        ModelingExercise modelingExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        participation = database.createAndSaveParticipationForExercise(modelingExercise, "student1");

        closeable = MockitoAnnotations.openMocks(this);
        participationTeamWebsocketService.clearDestinationTracker();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testSubscribeToParticipationTeamWebsocketTopic() {
        participationTeamWebsocketService.subscribe(participation.getId(), getStompHeaderAccessorMock());
        verify(messagingTemplate, times(1)).convertAndSend(websocketTopic(participation), List.of());
        assertThat(participationTeamWebsocketService.getDestinationTracker()).as("Session was added to destination tracker.").hasSize(1);
        assertThat(participationTeamWebsocketService.getDestinationTracker()).as("Destination in tracker is correct.").containsValue(websocketTopic(participation));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testTriggerSendOnlineTeamMembers() {
        participationTeamWebsocketService.triggerSendOnlineTeamStudents(participation.getId());
        verify(messagingTemplate, times(1)).convertAndSend(websocketTopic(participation), List.of());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testUnsubscribeFromParticipationTeamWebsocketTopic() {
        StompHeaderAccessor stompHeaderAccessor1 = getStompHeaderAccessorMock();
        StompHeaderAccessor stompHeaderAccessor2 = getStompHeaderAccessorMock();

        participationTeamWebsocketService.subscribe(participation.getId(), stompHeaderAccessor1);
        participationTeamWebsocketService.subscribe(participation.getId(), stompHeaderAccessor2);
        participationTeamWebsocketService.unsubscribe(stompHeaderAccessor1.getSessionId());

        verify(messagingTemplate, times(3)).convertAndSend(websocketTopic(participation), List.of());
        assertThat(participationTeamWebsocketService.getDestinationTracker()).as("Session was removed from destination tracker.").hasSize(1);
        assertThat(participationTeamWebsocketService.getDestinationTracker()).as("Correct session was removed.").containsKey(stompHeaderAccessor2.getSessionId());
    }

    private StompHeaderAccessor getStompHeaderAccessorMock() {
        String fakeSessionId = UUID.randomUUID().toString();
        StompHeaderAccessor stompHeaderAccessor = mock(StompHeaderAccessor.class, RETURNS_MOCKS);
        when(stompHeaderAccessor.getSessionId()).thenReturn(fakeSessionId);
        return stompHeaderAccessor;
    }
}
