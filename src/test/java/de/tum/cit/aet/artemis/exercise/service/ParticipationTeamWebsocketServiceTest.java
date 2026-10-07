package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.util.WebsocketDestinationMatchers.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionPatchDTO;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionSyncPayloadDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamModelingSubmissionUpdateDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamTextSubmissionUpdateDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.exercise.web.ParticipationTeamWebsocketService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.test_repository.TextSubmissionTestRepository;
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
    private TextSubmissionTestRepository textSubmissionRepository;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    private StudentParticipation participation;

    private StudentParticipation teamModelingParticipation;

    private StudentParticipation teamTextParticipation;

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

        ModelingExercise teamModelingExercise = modelingExerciseUtilService.addModelingExerciseToCourse(course);
        teamModelingParticipation = teamParticipationOfStudent1(teamModelingExercise);
        TextExercise teamTextExercise = textExerciseUtilService.createTeamTextExercise(textCourse, null, null, null);
        teamTextParticipation = teamParticipationOfStudent1(teamTextExercise);

        closeable = MockitoAnnotations.openMocks(this);
        participationTeamWebsocketService.clearDestinationTracker();
    }

    private StudentParticipation teamParticipationOfStudent1(Exercise exercise) {
        exercise.setMode(ExerciseMode.TEAM);
        exerciseRepository.save(exercise);
        Team team = teamUtilService.createTeam(Set.of(userUtilService.getUserByLogin(TEST_PREFIX + "student1")), userUtilService.getUserByLogin(TEST_PREFIX + "student3"), exercise,
                "team" + exercise.getId());
        return participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
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
        participationTeamWebsocketService.subscribe(participation.getId(), "fakeSessionId");
        verify(websocketMessagingService).sendMessage(topic(websocketTopic(participation)), eq(List.of()));
        assertThat(participationTeamWebsocketService.getDestinationTracker().getMapCopy()).as("Session was added to destination tracker.").hasSize(1);
        assertThat(participationTeamWebsocketService.getDestinationTracker().getMapCopy()).as("Destination in tracker is correct.").containsValue(websocketTopic(participation));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testIgnoresSubscriptionEventsThatWereNotAuthorized() {
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setSessionId("rejected-session");
        headers.setSubscriptionId("rejected-subscription");
        headers.setDestination(websocketTopic(teamTextParticipation));
        headers.setUser(getPrincipalMock("student2"));
        var event = new SessionSubscribeEvent(this, MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), headers.getUser());

        participationTeamWebsocketService.handleSubscribe(event);

        assertThat(participationTeamWebsocketService.getDestinationTracker().getMapCopy()).isEmpty();
        verify(websocketMessagingService, never()).sendMessage(topic(websocketTopic(teamTextParticipation)), any(Object.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTriggerSendOnlineTeamMembers() {
        participationTeamWebsocketService.triggerSendOnlineTeamStudents(teamTextParticipation.getId(), getPrincipalMock("student1"));
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(topic(websocketTopic(teamTextParticipation)), eq(List.of()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testTriggerSendOnlineTeamMembersIgnoresNonMembers() {
        participationTeamWebsocketService.triggerSendOnlineTeamStudents(teamTextParticipation.getId(), getPrincipalMock("student2"));
        verify(websocketMessagingService, after(1000).never()).sendMessage(topic(websocketTopic(teamTextParticipation)), any(Object.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUnsubscribeFromParticipationTeamWebsocketTopic() {
        participationTeamWebsocketService.subscribe(participation.getId(), "fakeSessionId1");
        participationTeamWebsocketService.subscribe(participation.getId(), "fakeSessionId2");
        participationTeamWebsocketService.unsubscribe("fakeSessionId1");

        verify(websocketMessagingService, timeout(2000).times(3)).sendMessage(topic(websocketTopic(participation)), eq(List.of()));
        assertThat(participationTeamWebsocketService.getDestinationTracker().getMapCopy()).as("Session was removed from destination tracker.").hasSize(1);
        assertThat(participationTeamWebsocketService.getDestinationTracker().getMapCopy()).as("Correct session was removed.").containsKey("fakeSessionId2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPatchModelingSubmission() {
        SubmissionPatchDTO patch = new SubmissionPatchDTO(null);

        // when we submit a patch ...
        participationTeamWebsocketService.patchModelingSubmission(participation.getId(), patch, getPrincipalMock("student1"));
        // the patch should be broadcast.
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(topic(websocketTopic(participation)), eq(List.of()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPatchModelingSubmissionWithWrongPrincipal() {
        SubmissionPatchDTO patch = new SubmissionPatchDTO(null);

        // when we submit a patch, but with the wrong user ...
        participationTeamWebsocketService.patchModelingSubmission(participation.getId(), patch, getPrincipalMock("student2"));
        // the patch should not be broadcast.
        verify(websocketMessagingService, after(1000).never()).sendMessage(topic(websocketTopic(participation)), eq(List.of()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateModelingSubmission() {
        TeamModelingSubmissionUpdateDTO submission = new TeamModelingSubmissionUpdateDTO(null, null, null, null);

        // when we submit a new modeling submission ...
        participationTeamWebsocketService.updateModelingSubmission(teamModelingParticipation.getId(), submission, getPrincipalMock("student1"));
        // the submission should be handled by the service (i.e. saved), ...
        verify(modelingSubmissionService, timeout(2000).times(1)).handleModelingSubmission(any(), any(), any(),
                argThat(target -> target.id() == teamModelingParticipation.getId()));
        // but it should NOT be broadcast (sync is handled with patches only).
        verify(websocketMessagingService, after(1000).never()).sendMessage(topic(websocketTopic(teamModelingParticipation)), eq(List.of()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateModelingSubmissionWithWrongPrincipal() {
        TeamModelingSubmissionUpdateDTO submission = new TeamModelingSubmissionUpdateDTO(null, null, null, null);

        // when we submit a new modeling submission with the wrong user ...
        participationTeamWebsocketService.updateModelingSubmission(teamModelingParticipation.getId(), submission, getPrincipalMock("student2"));
        // the submission is NOT saved ...
        verify(modelingSubmissionService, after(1000).never()).handleModelingSubmission(any(), any(), any(), argThat(target -> target.id() == teamModelingParticipation.getId()));
        // it is also not broadcast.
        verify(websocketMessagingService, after(1000).never()).sendMessage(topic(websocketTopic(teamModelingParticipation)), eq(List.of()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateTextSubmission() {
        TeamTextSubmissionUpdateDTO submission = new TeamTextSubmissionUpdateDTO(null, null, null, null);

        // when we submit a new text submission ...
        participationTeamWebsocketService.updateTextSubmission(teamTextParticipation.getId(), submission, getPrincipalMock("student1"));
        // the submission should be handled by the service (i.e. saved), ...
        verify(textSubmissionService, timeout(2000).times(1)).handleTextSubmission(any(), any(), any(), argThat(target -> target.id() == teamTextParticipation.getId()));
        // and it should be broadcast (unlike modeling exercises).
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(topic(websocketTopic(teamTextParticipation)), eq(List.of()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateTextSubmissionBroadcastsWhatTheReceivingEditorSavesBack() {
        TeamTextSubmissionUpdateDTO update = new TeamTextSubmissionUpdateDTO(null, "Hello team", Language.ENGLISH, true);

        participationTeamWebsocketService.updateTextSubmission(teamTextParticipation.getId(), update, getPrincipalMock("student1"));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(websocketMessagingService, timeout(2000)).sendMessage(topic(websocketTopic(teamTextParticipation) + "/text-submissions"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(SubmissionSyncPayloadDTO.class);
        SubmissionSyncPayloadDTO payload = (SubmissionSyncPayloadDTO) payloadCaptor.getValue();

        assertThat(payload.sender().login()).as("The receiving editor skips its own echo by matching this login").isEqualTo(TEST_PREFIX + "student1");
        // The receiving editor renders these and puts them straight back into PUT /api/text/exercises/{exerciseId}/text-submissions.
        assertThat(payload.submission().id()).as("The receiving editor saves back by id").isNotNull();
        assertThat(payload.submission().text()).as("The receiving editor renders the text").isEqualTo("Hello team");
        assertThat(payload.submission().language()).as("The receiving editor saves the language back").isEqualTo(Language.ENGLISH);
        assertThat(payload.submission().submitted()).as("The receiving editor saves the submitted flag back").isTrue();
        assertThat(payload.submission().participation()).as("The receiving editor rebuilds its participation from the payload").isNotNull();
        assertThat(payload.submission().participation().id()).isEqualTo(teamTextParticipation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateAssessedTextSubmissionWithoutResultsInPayloadStartsANewSubmission() {
        participationTeamWebsocketService.updateTextSubmission(teamTextParticipation.getId(), new TeamTextSubmissionUpdateDTO(null, "First", Language.ENGLISH, true),
                getPrincipalMock("student1"));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(websocketMessagingService, timeout(2000)).sendMessage(topic(websocketTopic(teamTextParticipation) + "/text-submissions"), payloadCaptor.capture());
        Long assessedSubmissionId = ((SubmissionSyncPayloadDTO) payloadCaptor.getValue()).submission().id();
        assertThat(assessedSubmissionId).isNotNull();

        TextSubmission assessedSubmission = textSubmissionRepository.findByIdWithParticipationExerciseResultAssessorElseThrow(assessedSubmissionId);
        assessedSubmission = (TextSubmission) participationUtilService.addResultToSubmission(assessedSubmission, AssessmentType.MANUAL);
        Long resultId = assessedSubmission.getLatestResult().getId();

        // The editor does not receive unreleased results, so the server must use the persisted result to protect the assessed submission.
        TeamTextSubmissionUpdateDTO update = new TeamTextSubmissionUpdateDTO(assessedSubmissionId, "Second", Language.ENGLISH, true);
        participationTeamWebsocketService.updateTextSubmission(teamTextParticipation.getId(), update, getPrincipalMock("student1"));

        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(topic(websocketTopic(teamTextParticipation) + "/text-submissions"), payloadCaptor.capture());
        SubmissionSyncPayloadDTO payload = (SubmissionSyncPayloadDTO) payloadCaptor.getAllValues().getLast();
        assertThat(payload.submission().id()).as("A result-bearing submission is not overwritten").isNotEqualTo(assessedSubmissionId);
        assertThat(payload.submission().text()).isEqualTo("Second");

        TextSubmission preservedSubmission = textSubmissionRepository.findByIdWithParticipationExerciseResultAssessorElseThrow(assessedSubmissionId);
        assertThat(preservedSubmission.getText()).as("The assessed text is unchanged").isEqualTo("First");
        assertThat(preservedSubmission.getResults()).as("The assessment is unchanged").singleElement().extracting(result -> result.getId()).isEqualTo(resultId);
    }

    /**
     * The working period of the team exercise is over and student1 practices on their own. A stale team editor that still sends to the team participation must neither write
     * into the practice participation nor tell the teammates about it.
     */
    private StudentParticipation endTheWorkingPeriodAndStartPracticing(StudentParticipation teamParticipation) {
        teamParticipation.setInitializationDate(ZonedDateTime.now().minusDays(2));
        studentParticipationRepository.save(teamParticipation);
        exerciseUtilService.updateExerciseDueDate(teamParticipation.getExercise().getId(), ZonedDateTime.now().minusDays(1));
        return participationUtilService.createAndSavePracticeParticipationForExercise(teamParticipation.getExercise(), TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateTextSubmissionAfterTheDueDateDoesNotWriteIntoThePracticeParticipation() {
        StudentParticipation practiceParticipation = endTheWorkingPeriodAndStartPracticing(teamTextParticipation);

        TeamTextSubmissionUpdateDTO update = new TeamTextSubmissionUpdateDTO(null, "Private practice text", Language.ENGLISH, true);
        assertThatThrownBy(() -> participationTeamWebsocketService.updateTextSubmission(teamTextParticipation.getId(), update, getPrincipalMock("student1")))
                .isInstanceOf(AccessForbiddenException.class);

        assertThat(submissionRepository.existsByParticipationId(practiceParticipation.getId())).as("The practice participation is not written to").isFalse();
        assertThat(submissionRepository.existsByParticipationId(teamTextParticipation.getId())).as("The team participation is not written to after the due date").isFalse();
        verify(websocketMessagingService, never()).sendMessage(topic(websocketTopic(teamTextParticipation) + "/text-submissions"), any(Object.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateModelingSubmissionAfterTheDueDateDoesNotWriteIntoThePracticeParticipation() {
        StudentParticipation practiceParticipation = endTheWorkingPeriodAndStartPracticing(teamModelingParticipation);

        TeamModelingSubmissionUpdateDTO update = new TeamModelingSubmissionUpdateDTO(null, null, null, null);
        assertThatThrownBy(() -> participationTeamWebsocketService.updateModelingSubmission(teamModelingParticipation.getId(), update, getPrincipalMock("student1")))
                .isInstanceOf(AccessForbiddenException.class);

        assertThat(submissionRepository.existsByParticipationId(practiceParticipation.getId())).as("The practice participation is not written to").isFalse();
        assertThat(submissionRepository.existsByParticipationId(teamModelingParticipation.getId())).as("The team participation is not written to after the due date").isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateTextSubmissionAddressedToThePracticeParticipationIsIgnored() {
        StudentParticipation practiceParticipation = participationUtilService.createAndSavePracticeParticipationForExercise(teamTextParticipation.getExercise(),
                TEST_PREFIX + "student1");

        TeamTextSubmissionUpdateDTO update = new TeamTextSubmissionUpdateDTO(null, "Private practice text", Language.ENGLISH, true);
        participationTeamWebsocketService.updateTextSubmission(practiceParticipation.getId(), update, getPrincipalMock("student1"));

        assertThat(submissionRepository.existsByParticipationId(practiceParticipation.getId())).isFalse();
        verify(websocketMessagingService, never()).sendMessage(topic(websocketTopic(practiceParticipation) + "/text-submissions"), any(Object.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStartTyping() {
        participationTeamWebsocketService.startTyping(teamTextParticipation.getId(), getPrincipalMock("student1"));
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(topic(websocketTopic(teamTextParticipation)), eq(List.of()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testStartTypingIgnoresNonMembers() {
        participationTeamWebsocketService.startTyping(teamTextParticipation.getId(), getPrincipalMock("student2"));
        verify(websocketMessagingService, after(1000).never()).sendMessage(topic(websocketTopic(teamTextParticipation)), any(Object.class));
    }

    private Principal getPrincipalMock(String username) {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(TEST_PREFIX + username);
        return principal;
    }
}
