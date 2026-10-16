package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class AuthorizationCheckServiceMaskedExerciseTest {

    private final AuthorizationCheckService authorizationCheckService = new AuthorizationCheckService(mock(UserRepository.class), mock(UserCourseRoleRepository.class),
            mock(TeamRepository.class), false);

    @Test
    void isAtLeastEditorForExerciseThrowsWhenExamOfExerciseGroupIsMasked() {
        var exercise = new TextExercise();
        exercise.setId(42L);
        var exerciseGroup = new ExerciseGroup();
        exerciseGroup.setExam(null);
        exercise.setExerciseGroup(exerciseGroup);

        assertThatThrownBy(() -> authorizationCheckService.isAtLeastEditorForExercise(exercise, new User())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exercise 42").hasMessageContaining("cannot be resolved");
    }

    @Test
    void isAtLeastEditorForExerciseThrowsWhenCourseMissing() {
        var exercise = new TextExercise();
        exercise.setId(7L);

        assertThatThrownBy(() -> authorizationCheckService.isAtLeastEditorForExercise(exercise, new User())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exercise 7");
    }
}
