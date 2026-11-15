package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExerciseTimelineValidationTest {

    @Test
    void equalInstantsAndAssessmentWithoutDueDateFailWithOriginalErrorMetadata() {
        var exercise = new TextExercise();
        exercise.setTitle("Exercise title");
        var group = new ExerciseVariantGroup();
        var release = ZonedDateTime.parse("2026-01-01T10:00:00Z");
        exercise.setReleaseDate(release);
        group.setReleaseDate(release);
        exercise.setDueDate(release.withZoneSameInstant(java.time.ZoneId.of("Europe/Berlin")));
        group.setDueDate(exercise.getDueDate());
        assertInvalid(exercise, group);
        exercise.setReleaseDate(null);
        group.setReleaseDate(null);
        exercise.setDueDate(null);
        group.setDueDate(null);
        exercise.setAssessmentDueDate(release);
        group.setAssessmentDueDate(release);
        assertInvalid(exercise, group);
    }

    @Test
    void examRestrictionAndEmptyTimelineBehaviorStaySpecificToExercises() {
        var exercise = new TextExercise();
        exercise.setTitle("Exam exercise");
        exercise.setExerciseGroup(new ExerciseGroup());
        assertThatCode(exercise::validateDates).doesNotThrowAnyException();
        exercise.setReleaseDate(ZonedDateTime.parse("2026-01-01T10:00:00Z"));
        assertThatThrownBy(exercise::validateDates).isInstanceOfSatisfying(BadRequestAlertException.class, failure -> {
            assertThat(failure.getErrorKey()).isEqualTo("invalidDatesForExamExercise");
            assertThat(failure.getEntityName()).isEqualTo("Exam exercise");
            assertThat(failure.getMessage()).isEqualTo("An exam exercise may not have any dates set!");
        });
        var group = new ExerciseVariantGroup();
        assertThatCode(group::validateDates).doesNotThrowAnyException();
        group.setReleaseDate(exercise.getReleaseDate());
        assertThatCode(group::validateDates).doesNotThrowAnyException();
    }

    private static void assertInvalid(TextExercise exercise, ExerciseVariantGroup group) {
        assertThatThrownBy(exercise::validateDates).isInstanceOfSatisfying(BadRequestAlertException.class, failure -> {
            assertThat(failure.getErrorKey()).isEqualTo("noValidDates");
            assertThat(failure.getEntityName()).isEqualTo("Exercise title");
            assertThat(failure.getMessage()).isEqualTo("The exercise dates are not valid");
        });
        assertThat(group.areDatesValid()).isFalse();
        assertThatThrownBy(group::validateDates).isInstanceOfSatisfying(BadRequestAlertException.class, exception -> {
            assertThat(exception.getErrorKey()).isEqualTo("noValidDates");
            assertThat(exception.getEntityName()).isEqualTo("exerciseVariantGroup");
            assertThat(exception.getMessage()).isEqualTo("The group dates are not valid");
        });
    }
}
