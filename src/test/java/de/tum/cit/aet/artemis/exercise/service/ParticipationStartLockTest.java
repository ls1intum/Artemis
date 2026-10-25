package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

@ExtendWith(MockitoExtension.class)
class ParticipationStartLockTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private StudentParticipationTestRepository studentParticipationRepository;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @InjectMocks
    private ParticipationService participationService;

    @Test
    void programmingTeamStartKeepsItsExistingSetupPathOutsideTheDatabaseLock() {
        var exercise = new ProgrammingExercise();
        exercise.setId(42L);
        var team = new Team();
        team.setId(7L);
        var participation = new ProgrammingExerciseStudentParticipation();
        when(studentParticipationRepository.findWithEagerSubmissionsAndTeamStudentsByExerciseIdAndTeamId(exercise.getId(), team.getId())).thenReturn(Optional.of(participation));
        var templateLookupFailure = new IllegalStateException("template lookup");
        when(programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(exercise.getId())).thenThrow(templateLookupFailure);

        assertThatThrownBy(() -> participationService.startExercise(exercise, team, true)).isSameAs(templateLookupFailure);

        verifyNoInteractions(teamRepository);
    }
}
