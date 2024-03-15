package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.modeling.ModelingSubmissionPatch;

class ModelingSubmissionPatchTest {

    @Test
    void checkExerciseType() {
        final ModelingSubmissionPatch modelingSubmissionPatch = new ModelingSubmissionPatch();
        assertThat(modelingSubmissionPatch.getSubmissionExerciseType()).isEqualTo("modeling");
    }
}
