package de.tum.cit.aet.artemis.athena;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.athena.dto.ModelingExerciseDTO;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

class ModelingExerciseDTOTest {

    @Test
    void missingGradingCriteria_areRepresentedAsAnEmptyList() {
        var exercise = new ModelingExercise();
        exercise.setId(1L);
        exercise.setMaxPoints(10.0);
        exercise.setBonusPoints(0.0);
        exercise.setGradingCriteria(null);

        assertThat(ModelingExerciseDTO.of(exercise).gradingCriteria()).isEmpty();
    }
}
