package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
