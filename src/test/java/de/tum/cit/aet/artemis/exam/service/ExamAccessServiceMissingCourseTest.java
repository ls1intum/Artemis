package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Plain unit test for the guard against exam exercises whose course cannot be resolved (masked exam graph).
 */
class ExamAccessServiceMissingCourseTest {

    @Test
    void checkIfAllowedToGetExamResult_examMaskedOut_throwsEntityNotFound() {
        var service = new ExamAccessService(null, null, null, null, null, null, null, null);
        var exerciseGroup = new ExerciseGroup();
        exerciseGroup.setExam(null);
        var exercise = new TextExercise();
        exercise.setId(42L);
        exercise.setExerciseGroup(exerciseGroup);

        assertThatThrownBy(() -> service.checkIfAllowedToGetExamResult(exercise, null, new User())).isInstanceOf(EntityNotFoundException.class).hasMessageContaining("42");
    }
}
