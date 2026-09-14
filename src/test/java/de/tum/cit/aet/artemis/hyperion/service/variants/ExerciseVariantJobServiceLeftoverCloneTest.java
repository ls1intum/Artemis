package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;

/**
 * The terminal transitions clear {@code variantExerciseId} because the hard-failure and cancellation paths delete
 * the provisioned clone first. When that deletion FAILS, the clone (with its repositories and build plans)
 * survives and the id is the only pointer to it, so the pipeline uses the id-preserving transitions instead.
 * These tests pin that behavior.
 */
class ExerciseVariantJobServiceLeftoverCloneTest {

    private static final String LOGIN = "instructor1";

    private static final String CLEANUP_DETAIL = "The generated exercise (id 42) could not be deleted automatically — delete it manually.";

    private ExerciseVariantJobService jobService;

    private VariantJob job;

    @BeforeEach
    void setUp() {
        jobService = new ExerciseVariantJobService(new LocalDataProviderService(), mock(HyperionWebsocketService.class));
        jobService.init();

        Exercise exercise = mock(Exercise.class);
        when(exercise.getId()).thenReturn(1L);
        when(exercise.getTitle()).thenReturn("Test Exercise");
        when(exercise.getExerciseType()).thenReturn(ExerciseType.PROGRAMMING);
        User user = mock(User.class);
        when(user.getLogin()).thenReturn(LOGIN);

        job = jobService.startJob(user, exercise, mock(VariantGenerationRequestDTO.class));
        jobService.recordVariantExerciseId(job.getJobId(), 42L);
    }

    @Test
    void shouldKeepTheExerciseIdWhenCancellingWithALeftoverClone() {
        jobService.markCancelledKeepingVariantExerciseId(job.getJobId(), CLEANUP_DETAIL);

        VariantJob stored = jobService.getJob(job.getJobId(), LOGIN).orElseThrow();
        assertThat(stored.getPhase()).isEqualTo(VariantJobPhase.CANCELLED);
        assertThat(stored.getVariantExerciseId()).isEqualTo(42L);
        assertThat(stored.getFailureDetail()).isEqualTo(CLEANUP_DETAIL);
    }

    @Test
    void shouldClearTheExerciseIdWhenCancellingAfterASuccessfulCleanup() {
        jobService.markCancelled(job.getJobId());

        VariantJob stored = jobService.getJob(job.getJobId(), LOGIN).orElseThrow();
        assertThat(stored.getPhase()).isEqualTo(VariantJobPhase.CANCELLED);
        assertThat(stored.getVariantExerciseId()).isNull();
    }

    @Test
    void shouldKeepTheExerciseIdAndTheSummaryWhenFailingWithALeftoverClone() {
        jobService.failKeepingVariantExerciseId(job.getJobId(), "Failed in VERIFYING. " + CLEANUP_DETAIL, "Next steps");

        VariantJob stored = jobService.getJob(job.getJobId(), LOGIN).orElseThrow();
        assertThat(stored.getPhase()).isEqualTo(VariantJobPhase.FAILED);
        assertThat(stored.getVariantExerciseId()).isEqualTo(42L);
        assertThat(stored.getFailureDetail()).contains(CLEANUP_DETAIL);
        assertThat(stored.getInstructorSummary()).isEqualTo("Next steps");
    }

    @Test
    void shouldClearTheExerciseIdWhenFailingAfterASuccessfulCleanup() {
        jobService.fail(job.getJobId(), "Failed in VERIFYING", "Next steps");

        VariantJob stored = jobService.getJob(job.getJobId(), LOGIN).orElseThrow();
        assertThat(stored.getPhase()).isEqualTo(VariantJobPhase.FAILED);
        assertThat(stored.getVariantExerciseId()).isNull();
    }
}
