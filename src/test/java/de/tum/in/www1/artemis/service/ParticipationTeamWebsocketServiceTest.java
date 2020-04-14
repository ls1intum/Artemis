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
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.web.websocket.team.ParticipationTeamWebsocketService;

class ParticipationTeamWebsocketServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    ModelingExerciseRepository exerciseRepo;

    @Autowired
    StudentParticipationRepository participationRepo;

    @Autowired
    ParticipationTeamWebsocketService participationTeamWebsocketService;

    ModelingExercise exercise;

    StudentParticipation participation;

    static String websocketTopic(Participation participation) {
        return "/topic/participations/" + participation.getId() + "/team";
    }

    @BeforeEach
    void init() {
        database.addUsers(3, 0, 0);
        database.addCourseWithOneModelingExercise();
        exercise = exerciseRepo.findAll().get(0);
        participation = database.addParticipationForExercise(exercise, "student1");

        MockitoAnnotations.initMocks(this);
        participationTeamWebsocketService.clearDestinationTracker();
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testSubscribeToParticipationTeamWebsocketTopic() {
        participationTeamWebsocketService.subscribe(participation.getId(), getStompHeaderAccessorMock());
        participationTeamWebsocketService.subscribe(participation.getId(), getStompHeaderAccessorMock());
        verify(messagingTemplate, times(2)).convertAndSend(websocketTopic(participation), List.of());
        assertThat(participationTeamWebsocketService.getDestinationTracker()).as("Sessions were added to destination tracker.").hasSize(2);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testTriggerSendOnlineTeamMembers() {
        participationTeamWebsocketService.triggerSend(participation.getId());
        verify(messagingTemplate, times(1)).convertAndSend(websocketTopic(participation), List.of());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testUnsubscribeFromParticipationTeamWebsocketTopic() {
        StompHeaderAccessor stompHeaderAccessor = getStompHeaderAccessorMock();
        participationTeamWebsocketService.subscribe(participation.getId(), stompHeaderAccessor);
        participationTeamWebsocketService.unsubscribe(stompHeaderAccessor.getSessionId());
        verify(messagingTemplate, times(2)).convertAndSend(websocketTopic(participation), List.of());
        assertThat(participationTeamWebsocketService.getDestinationTracker()).as("Session was removed from destination tracker.").hasSize(0);
    }

    private StompHeaderAccessor getStompHeaderAccessorMock() {
        String fakeSessionId = UUID.randomUUID().toString();
        StompHeaderAccessor stompHeaderAccessor = mock(StompHeaderAccessor.class, RETURNS_MOCKS);
        when(stompHeaderAccessor.getSessionId()).thenReturn(fakeSessionId);
        return stompHeaderAccessor;
    }
}
