package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * The provisioners signal a clone they could not delete themselves with a {@link LeftoverVariantExerciseException},
 * which reaches the pipeline wrapped in the phase failure. Its id decides between the clearing and the
 * id-preserving terminal transition (see {@link ExerciseVariantJobServiceLeftoverCloneTest}), so the extraction
 * has to survive an arbitrary cause chain.
 */
class ExerciseVariantGenerationPipelineLeftoverIdTest {

    @Test
    void shouldFindTheLeftoverIdThroughTheCauseChain() {
        Throwable wrapped = new IllegalStateException("Failed in PROVISIONING", new RuntimeException("wrapper", new LeftoverVariantExerciseException(77L, "boom", null)));

        assertThat(ExerciseVariantGenerationPipelineService.leftoverExerciseId(wrapped)).isEqualTo(77L);
    }

    @Test
    void shouldReturnNullWhenNothingWasLeftBehind() {
        assertThat(ExerciseVariantGenerationPipelineService.leftoverExerciseId(new IllegalStateException("Failed in PLANNING", new IOException("no plan")))).isNull();
        assertThat(ExerciseVariantGenerationPipelineService.leftoverExerciseId(null)).isNull();
    }

    /** A self-referencing cause must not spin forever. */
    @Test
    void shouldTerminateOnASelfReferencingCause() {
        Throwable selfReferencing = new IllegalStateException("boom") {

            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        assertThat(ExerciseVariantGenerationPipelineService.leftoverExerciseId(selfReferencing)).isNull();
    }
}
