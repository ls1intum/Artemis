package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionPatchDTO;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionSyncPayloadDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamModelingSubmissionUpdateDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamTextSubmissionUpdateDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.exercise.web.ParticipationTeamWebsocketService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ParticipationTeamWebsocketServiceTest extends AbstractSpringIntegrationIndependentBatchTest {

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
    private ParticipationUtilService participationUtilService;

    @Autowired
    private SubmissionTestRepository submissionTestRepository;

    private StudentParticipation participation;

    private StudentParticipation textParticipation;

    private static String websocketTopic(Participation participation) {
        return "/topic/participations/" + participation.getId() + "/team";
    }

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 0);
        Course course = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("ClassDiagram", TEST_PREFIX);
        ModelingExercise modelingExercise = ExerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        participation = participationUtilService.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "student1");

        Course textCourse = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        TextExercise textExercise = ExerciseUtilService.findTextExerciseWithTitle(textCourse.getExercises(), "Text");
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
        assertThat(participationTeamWebsocketService.getDestinationTracker().getMapCopy()).as("Session was added to destination tracker.").hasSize(1);
        assertThat(participationTeamWebsocketService.getDestinationTracker().getMapCopy()).as("Destination in tracker is correct.").containsValue(websocketTopic(participation));
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
        assertThat(participationTeamWebsocketService.getDestinationTracker().getMapCopy()).as("Session was removed from destination tracker.").hasSize(1);
        assertThat(participationTeamWebsocketService.getDestinationTracker().getMapCopy()).as("Correct session was removed.").containsKey(stompHeaderAccessor2.getSessionId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPatchModelingSubmission() {
        SubmissionPatchDTO patch = new SubmissionPatchDTO(null);

        // when we submit a patch ...
        participationTeamWebsocketService.patchModelingSubmission(participation.getId(), patch, getPrincipalMock("student1"));
        // the patch should be broadcast.
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(websocketTopic(participation), List.of());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPatchModelingSubmissionWithWrongPrincipal() {
        SubmissionPatchDTO patch = new SubmissionPatchDTO(null);

        // when we submit a patch, but with the wrong user ...
        participationTeamWebsocketService.patchModelingSubmission(participation.getId(), patch, getPrincipalMock("student2"));
        // the patch should not be broadcast.
        verify(websocketMessagingService, after(1000).never()).sendMessage(websocketTopic(participation), List.of());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateModelingSubmission() {
        TeamModelingSubmissionUpdateDTO submission = new TeamModelingSubmissionUpdateDTO(null, null, null, null);

        // when we submit a new modeling submission ...
        participationTeamWebsocketService.updateModelingSubmission(participation.getId(), submission, getPrincipalMock("student1"));
        // the submission should be handled by the service (i.e. saved), ...
        verify(modelingSubmissionService, timeout(2000).times(1)).handleModelingSubmission(any(), any(), any(), isNull());
        // but it should NOT be broadcast (sync is handled with patches only).
        verify(websocketMessagingService, after(1000).never()).sendMessage(websocketTopic(participation), List.of());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateModelingSubmissionWithWrongPrincipal() {
        TeamModelingSubmissionUpdateDTO submission = new TeamModelingSubmissionUpdateDTO(null, null, null, null);

        // when we submit a new modeling submission with the wrong user ...
        participationTeamWebsocketService.updateModelingSubmission(participation.getId(), submission, getPrincipalMock("student2"));
        // the submission is NOT saved ...
        verify(modelingSubmissionService, after(1000).never()).handleModelingSubmission(any(), any(), any(), isNull());
        // it is also not broadcast.
        verify(websocketMessagingService, after(1000).never()).sendMessage(websocketTopic(participation), List.of());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateTextSubmission() {
        TeamTextSubmissionUpdateDTO submission = new TeamTextSubmissionUpdateDTO(null, null, null, null, null);

        // when we submit a new text submission ...
        participationTeamWebsocketService.updateTextSubmission(textParticipation.getId(), submission, getPrincipalMock("student1"));
        // the submission should be handled by the service (i.e. saved), ...
        verify(textSubmissionService, timeout(2000).times(1)).handleTextSubmission(any(), any(), any(), isNull());
        // and it should be broadcast (unlike modeling exercises).
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(websocketTopic(textParticipation), List.of());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateTextSubmissionBroadcastsWhatTheReceivingEditorSavesBack() {
        TeamTextSubmissionUpdateDTO update = new TeamTextSubmissionUpdateDTO(null, "Hello team", Language.ENGLISH, true, null);

        participationTeamWebsocketService.updateTextSubmission(textParticipation.getId(), update, getPrincipalMock("student1"));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(websocketMessagingService, timeout(2000)).sendMessage(eq(websocketTopic(textParticipation) + "/text-submissions"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(SubmissionSyncPayloadDTO.class);
        SubmissionSyncPayloadDTO payload = (SubmissionSyncPayloadDTO) payloadCaptor.getValue();

        assertThat(payload.sender().login()).as("The receiving editor skips its own echo by matching this login").isEqualTo(TEST_PREFIX + "student1");
        // The receiving editor renders these and puts them straight back into PUT /api/text/exercises/{exerciseId}/text-submissions.
        assertThat(payload.submission().id()).as("The receiving editor saves back by id").isNotNull();
        assertThat(payload.submission().text()).as("The receiving editor renders the text").isEqualTo("Hello team");
        assertThat(payload.submission().language()).as("The receiving editor saves the language back").isEqualTo(Language.ENGLISH);
        assertThat(payload.submission().submitted()).as("The receiving editor saves the submitted flag back").isTrue();
        assertThat(payload.submission().participation()).as("The receiving editor rebuilds its participation from the payload").isNotNull();
        assertThat(payload.submission().participation().id()).isEqualTo(textParticipation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateTextSubmissionWithResultsStartsANewSubmission() {
        participationTeamWebsocketService.updateTextSubmission(textParticipation.getId(), new TeamTextSubmissionUpdateDTO(null, "First", Language.ENGLISH, true, null),
                getPrincipalMock("student1"));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(websocketMessagingService, timeout(2000)).sendMessage(eq(websocketTopic(textParticipation) + "/text-submissions"), payloadCaptor.capture());
        Long assessedSubmissionId = ((SubmissionSyncPayloadDTO) payloadCaptor.getValue()).submission().id();
        assertThat(assessedSubmissionId).isNotNull();

        // The editor holds a result for that submission, so the next update must not overwrite the assessed one.
        TeamTextSubmissionUpdateDTO update = new TeamTextSubmissionUpdateDTO(assessedSubmissionId, "Second", Language.ENGLISH, true,
                List.of(new TeamTextSubmissionUpdateDTO.ResultIdDTO(1L)));
        participationTeamWebsocketService.updateTextSubmission(textParticipation.getId(), update, getPrincipalMock("student1"));

        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(eq(websocketTopic(textParticipation) + "/text-submissions"), payloadCaptor.capture());
        SubmissionSyncPayloadDTO payload = (SubmissionSyncPayloadDTO) payloadCaptor.getAllValues().getLast();
        assertThat(payload.submission().id()).as("A submission the client holds a result for is not overwritten").isNotEqualTo(assessedSubmissionId);
        assertThat(payload.submission().text()).isEqualTo("Second");
        assertThat(submissionTestRepository.findById(assessedSubmissionId)).as("The assessed submission is kept").isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStartTyping() {
        participationTeamWebsocketService.startTyping(participation.getId(), getPrincipalMock("student1"));
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(websocketTopic(participation), List.of());
    }

    private StompHeaderAccessor getStompHeaderAccessorMock(String fakeSessionId) {
        StompHeaderAccessor stompHeaderAccessor = mock(StompHeaderAccessor.class, RETURNS_MOCKS);
        when(stompHeaderAccessor.getSessionId()).thenReturn(fakeSessionId);
        return stompHeaderAccessor;
    }

    private Principal getPrincipalMock(String username) {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(TEST_PREFIX + username);
        return principal;
    }
}
