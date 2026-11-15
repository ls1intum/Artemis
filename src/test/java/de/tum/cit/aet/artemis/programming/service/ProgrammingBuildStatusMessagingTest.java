package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.util.WebsocketDestinationMatchers.topic;
import static de.tum.cit.aet.artemis.core.util.WebsocketDestinationMatchers.userTopic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionDTO;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.dto.SubmissionProcessingDTO;

@ExtendWith(MockitoExtension.class)
class ProgrammingBuildStatusMessagingTest {

    @Mock
    private WebsocketMessagingService websocketMessagingService;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private ParticipationTestRepository participationRepository;

    private ProgrammingSubmissionMessagingService submissionMessagingService;

    private ProgrammingMessagingService programmingMessagingService;

    @BeforeEach
    void setUp() {
        submissionMessagingService = new ProgrammingSubmissionMessagingService(teamRepository, websocketMessagingService);
        programmingMessagingService = new ProgrammingMessagingService(null, websocketMessagingService, null, Optional.empty(), teamRepository, Optional.empty(),
                participationRepository);
    }

    private static Stream<Participation> participations() {
        User student = new User();
        student.setLogin("student");
        student.setId(1L);
        var individual = new ProgrammingExerciseStudentParticipation();
        individual.setParticipant(student);
        var team = new Team();
        team.setId(2L);
        team.addStudents(student);
        var teamParticipation = new ProgrammingExerciseStudentParticipation();
        teamParticipation.setParticipant(team);
        return Stream.of(individual, teamParticipation, new TemplateProgrammingExerciseParticipation(), new SolutionProgrammingExerciseParticipation());
    }

    private void prepareParticipation(Participation participation) {
        participation.setId(42L);
        if (participation instanceof StudentParticipation studentParticipation && studentParticipation.getParticipant() instanceof Team team) {
            when(teamRepository.findWithStudentsByIdElseThrow(team.getId())).thenReturn(team);
        }
    }

    @ParameterizedTest
    @MethodSource("participations")
    void sendsNewSubmissionsToStaffAndTheirOwners(Participation participation) {
        prepareParticipation(participation);
        var submission = new ProgrammingSubmission();
        submission.setId(99L);
        submission.setParticipation(participation);
        submission.setCommitHash("commit-hash");

        submissionMessagingService.notifyUserAboutSubmission(submission, 10L);

        var payload = ArgumentCaptor.forClass(SubmissionDTO.class);
        verify(websocketMessagingService).sendMessage(topic("/topic/exercise/10/newSubmissions"), payload.capture());
        assertThat(payload.getValue().id()).isEqualTo(99L);
        assertThat(payload.getValue().participation().id()).isEqualTo(42L);
        assertThat(payload.getValue().commitHash()).isEqualTo("commit-hash");
        if (participation instanceof StudentParticipation) {
            verify(websocketMessagingService).sendMessageToUser(eq("student"), userTopic("/topic/newSubmissions"), eq(payload.getValue()));
        }
    }

    @ParameterizedTest
    @MethodSource("participations")
    void sendsProcessingUpdatesToStaffAndTheirOwners(Participation participation) {
        prepareParticipation(participation);
        when(participationRepository.findWithProgrammingExerciseById(42L)).thenReturn(Optional.of(participation));
        var now = ZonedDateTime.now();
        var processing = new SubmissionProcessingDTO(10L, 42L, "commit-hash", now, now, now.plusMinutes(1));

        programmingMessagingService.notifyUserAboutSubmissionProcessing(processing, 10L, 42L);

        verify(websocketMessagingService).sendMessage(topic("/topic/exercise/10/submissionProcessing"), eq(processing));
        if (participation instanceof StudentParticipation) {
            verify(websocketMessagingService).sendMessageToUser(eq("student"), userTopic("/topic/submissionProcessing"), eq(processing));
        }
    }
}
