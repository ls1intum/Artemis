package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.dto.CreateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.UpdateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Pure unit tests for the {@link ExerciseVariantGroup} entity and its DTO mappings, without a Spring context.
 */
class ExerciseVariantGroupTest {

    @Test
    void testAddExerciseKeepsBothSidesConsistent() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        TextExercise exercise = new TextExercise();

        group.addExercise(exercise);

        assertThat(group.getExercises()).containsExactly(exercise);
        assertThat(exercise.getExerciseVariantGroup()).isEqualTo(group);
    }

    @Test
    void testRemoveExerciseClearsBothSides() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        TextExercise exercise = new TextExercise();
        group.addExercise(exercise);

        group.removeExercise(exercise);

        assertThat(group.getExercises()).isEmpty();
        assertThat(exercise.getExerciseVariantGroup()).isNull();
    }

    @Test
    void testTitleIsStripped() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setTitle("  Loop variants  ");
        assertThat(group.getTitle()).isEqualTo("Loop variants");
    }

    @Test
    void testCreateDtoToEntityMapsAllSettings() {
        CreateExerciseVariantGroupDTO dto = new CreateExerciseVariantGroupDTO("Loop variants", 100.0, null, null, null, null, null, null);

        ExerciseVariantGroup entity = dto.toEntity();

        assertThat(entity.getTitle()).isEqualTo("Loop variants");
        assertThat(entity.getMaxPoints()).isEqualTo(100.0);
        assertThat(entity.getExercises()).isEmpty();
    }

    @Test
    void testUpdateDtoApplyToLeavesExercisesUntouched() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.addExercise(new TextExercise());
        UpdateExerciseVariantGroupDTO dto = new UpdateExerciseVariantGroupDTO(1L, "Renamed", 50.0, null, null, null, null, null, null);

        dto.applyTo(group);

        assertThat(group.getTitle()).isEqualTo("Renamed");
        assertThat(group.getMaxPoints()).isEqualTo(50.0);
        assertThat(group.getExercises()).hasSize(1);
    }

    @Test
    void testEntityToDtoExposesExerciseIds() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setId(7L);
        group.setTitle("Loop variants");
        TextExercise exercise = new TextExercise();
        exercise.setId(42L);
        group.addExercise(exercise);

        ExerciseVariantGroupDTO dto = new ExerciseVariantGroupDTO(group);

        assertThat(dto.id()).isEqualTo(7L);
        assertThat(dto.title()).isEqualTo("Loop variants");
        assertThat(dto.exerciseIds()).containsExactly(42L);
    }

    private static final ZonedDateTime BASE = ZonedDateTime.parse("2026-01-01T00:00:00Z");

    @Test
    void testDatesAreValidWhenAllUnset() {
        // An entirely unset timeline has no ordering to violate.
        assertThat(new ExerciseVariantGroup().areDatesValid()).isTrue();
    }

    @Test
    void testDatesAreValidForAConsistentTimeline() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setReleaseDate(BASE);
        group.setStartDate(BASE.plusDays(1));
        group.setDueDate(BASE.plusDays(2));
        group.setAssessmentDueDate(BASE.plusDays(3));
        group.setExampleSolutionPublicationDate(BASE.plusDays(4));

        assertThat(group.areDatesValid()).isTrue();
    }

    @Test
    void testReleaseDateAfterDueDateIsInvalid() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setReleaseDate(BASE.plusDays(2));
        group.setDueDate(BASE.plusDays(1));

        assertThat(group.areDatesValid()).isFalse();
    }

    @Test
    void testReleaseDateAfterStartDateIsInvalid() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setReleaseDate(BASE.plusDays(2));
        group.setStartDate(BASE.plusDays(1));

        assertThat(group.areDatesValid()).isFalse();
    }

    @Test
    void testStartDateAfterDueDateIsInvalid() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setStartDate(BASE.plusDays(2));
        group.setDueDate(BASE.plusDays(1));

        assertThat(group.areDatesValid()).isFalse();
    }

    @Test
    void testAssessmentDueDateWithoutDueDateIsInvalid() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        // There cannot be an assessment due date without a due date.
        group.setAssessmentDueDate(BASE.plusDays(1));

        assertThat(group.areDatesValid()).isFalse();
    }

    @Test
    void testAssessmentDueDateBeforeDueDateIsInvalid() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setDueDate(BASE.plusDays(2));
        group.setAssessmentDueDate(BASE.plusDays(1));

        assertThat(group.areDatesValid()).isFalse();
    }

    @Test
    void testExampleSolutionPublicationDateBeforeDueDateIsAllowed() {
        // Unlike a single exercise, the group cannot decide the "example solution not before the due date" rule
        // (it has no IncludedInOverallScore); each member exercise re-validates it when the group timeline is applied.
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setDueDate(BASE.plusDays(2));
        group.setExampleSolutionPublicationDate(BASE.plusDays(1));

        assertThat(group.areDatesValid()).isTrue();
    }

    @Test
    void testExampleSolutionPublicationDateBeforeReleaseDateIsInvalid() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setReleaseDate(BASE.plusDays(2));
        group.setExampleSolutionPublicationDate(BASE.plusDays(1));

        assertThat(group.areDatesValid()).isFalse();
    }

    @Test
    void testEqualBoundaryDatesAreValid() {
        // The ordering rules are inclusive (not strictly increasing), so coincident dates are allowed.
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setReleaseDate(BASE);
        group.setStartDate(BASE);
        group.setDueDate(BASE);
        group.setAssessmentDueDate(BASE);
        group.setExampleSolutionPublicationDate(BASE);

        assertThat(group.areDatesValid()).isTrue();
    }

    @Test
    void testValidateDatesThrowsOnInconsistentTimeline() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setReleaseDate(BASE.plusDays(2));
        group.setDueDate(BASE.plusDays(1));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(group::validateDates);
    }

    @Test
    void testValidateDatesPassesForConsistentTimeline() {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setReleaseDate(BASE);
        group.setDueDate(BASE.plusDays(1));

        assertThatCode(group::validateDates).doesNotThrowAnyException();
    }
}
