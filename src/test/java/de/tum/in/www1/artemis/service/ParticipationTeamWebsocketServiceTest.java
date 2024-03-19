package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.websocket.dto.SubmissionPatch;
import de.tum.in.www1.artemis.web.websocket.team.ParticipationTeamWebsocketService;

class ParticipationTeamWebsocketServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "participationteamwebsocket";

    @Autowired
    private ParticipationTeamWebsocketService participationTeamWebsocketService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private StudentParticipation participation;

    private StudentParticipation textParticipation;

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

        Course textCourse = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise textExercise = exerciseUtilService.findTextExerciseWithTitle(textCourse.getExercises(), "Text");
        textParticipation = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");

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
        verify(websocketMessagingService).sendMessage(websocketTopic(participation), List.of());
        assertThat(participationTeamWebsocketService.getDestinationTracker()).as("Session was added to destination tracker.").hasSize(1);
        assertThat(participationTeamWebsocketService.getDestinationTracker()).as("Destination in tracker is correct.").containsValue(websocketTopic(participation));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTriggerSendOnlineTeamMembers() {
        participationTeamWebsocketService.triggerSendOnlineTeamStudents(participation.getId());
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(websocketTopic(participation), List.of());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUnsubscribeFromParticipationTeamWebsocketTopic() {
        StompHeaderAccessor stompHeaderAccessor1 = getStompHeaderAccessorMock("fakeSessionId1");
        StompHeaderAccessor stompHeaderAccessor2 = getStompHeaderAccessorMock("fakeSessionId2");

        participationTeamWebsocketService.subscribe(participation.getId(), stompHeaderAccessor1);
        participationTeamWebsocketService.subscribe(participation.getId(), stompHeaderAccessor2);
        participationTeamWebsocketService.unsubscribe(stompHeaderAccessor1.getSessionId());

        verify(websocketMessagingService, timeout(2000).times(3)).sendMessage(websocketTopic(participation), List.of());
        assertThat(participationTeamWebsocketService.getDestinationTracker()).as("Session was removed from destination tracker.").hasSize(1);
        assertThat(participationTeamWebsocketService.getDestinationTracker()).as("Correct session was removed.").containsKey(stompHeaderAccessor2.getSessionId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPatchModelingSubmission() {
        SubmissionPatch patch = new SubmissionPatch(participation, null);

        // when we submit a patch ...
        participationTeamWebsocketService.patchModelingSubmission(participation.getId(), patch, getPrincipalMock());
        // the patch should be broadcast.
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(websocketTopic(participation), List.of());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPatchModelingSubmissionWithWrongPrincipal() {
        SubmissionPatch patch = new SubmissionPatch(participation, null);

        // when we submit a patch, but with the wrong user ...
        participationTeamWebsocketService.patchModelingSubmission(participation.getId(), patch, getPrincipalMock("student2"));
        // the patch should not be broadcast.
        verify(websocketMessagingService, timeout(2000).times(0)).sendMessage(websocketTopic(participation), List.of());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateModelingSubmission() {
        ModelingSubmission submission = new ModelingSubmission();

        // when we submit a new modeling submission ...
        participationTeamWebsocketService.updateModelingSubmission(participation.getId(), submission, getPrincipalMock());
        // the submission should be handled by the service (i.e. saved), ...
        verify(modelingSubmissionService, timeout(2000).times(1)).handleModelingSubmission(any(), any(), any());
        // but it should NOT be broadcast (sync is handled with patches only).
        verify(websocketMessagingService, timeout(2000).times(0)).sendMessage(websocketTopic(participation), List.of());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateModelingSubmissionWithWrongPrincipal() {
        ModelingSubmission submission = new ModelingSubmission();

        // when we submit a new modeling submission with the wrong user ...
        participationTeamWebsocketService.updateModelingSubmission(participation.getId(), submission, getPrincipalMock("student2"));
        // the submission is NOT saved ...
        verify(modelingSubmissionService, timeout(2000).times(0)).handleModelingSubmission(any(), any(), any());
        // it is also not broadcast.
        verify(websocketMessagingService, timeout(2000).times(0)).sendMessage(websocketTopic(participation), List.of());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateTextSubmission() {
        TextSubmission submission = new TextSubmission();

        // when we submit a new text submission ...
        participationTeamWebsocketService.updateTextSubmission(textParticipation.getId(), submission, getPrincipalMock());
        // the submission should be handled by the service (i.e. saved), ...
        verify(textSubmissionService, timeout(2000).times(1)).handleTextSubmission(any(), any(), any());
        // and it should be broadcast (unlike modeling exercises).
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(websocketTopic(textParticipation), List.of());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStartTyping() {
        participationTeamWebsocketService.startTyping(participation.getId(), getPrincipalMock());
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(websocketTopic(participation), List.of());
    }

    private StompHeaderAccessor getStompHeaderAccessorMock(String fakeSessionId) {
        StompHeaderAccessor stompHeaderAccessor = mock(StompHeaderAccessor.class, RETURNS_MOCKS);
        when(stompHeaderAccessor.getSessionId()).thenReturn(fakeSessionId);
        return stompHeaderAccessor;
    }

    private Principal getPrincipalMock() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(TEST_PREFIX + "student1");
        return principal;
    }

    private Principal getPrincipalMock(String username) {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(TEST_PREFIX + username);
        return principal;
    }
}
