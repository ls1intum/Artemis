package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@ExtendWith(MockitoExtension.class)
class ParticipationAuthorizationCheckServiceNoCourseTest {

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private AuthorizationCheckService authCheckService;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private ExerciseDateService exerciseDateService;

    @Mock
    private SubmissionPolicyRepository submissionPolicyRepository;

    @Mock
    private SubmissionTestRepository submissionRepository;

    @Mock
    private StudentParticipationTestRepository studentParticipationRepository;

    @Test
    void canAccessParticipation_exerciseWithoutCourse_throwsIllegalState() {
        var service = new ParticipationAuthorizationCheckService(userRepository, programmingExerciseRepository, authCheckService, teamRepository, exerciseDateService,
                submissionPolicyRepository, submissionRepository, Optional.empty(), studentParticipationRepository);

        var exercise = new TextExercise();
        var participation = new StudentParticipation();
        participation.setId(11L);
        participation.setExercise(exercise);
        when(authCheckService.isOwnerOfParticipation(participation)).thenReturn(false);

        assertThatThrownBy(() -> service.canAccessParticipation(participation, new User())).isInstanceOf(IllegalStateException.class);
    }
}
