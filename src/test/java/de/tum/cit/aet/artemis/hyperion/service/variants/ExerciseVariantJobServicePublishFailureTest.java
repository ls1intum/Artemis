package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;

/**
 * Regression test: a failed websocket publish must not undo a persisted terminal transition.
 * <p>
 * Every terminal transition writes the new phase first and only then publishes. When the publish threw, the
 * exception escaped {@code complete()} into the pipeline's terminal catch, which deleted the freshly generated
 * variant exercise and overwrote COMPLETED with FAILED — a lost notification destroying verified work.
 * Publication is best effort: the client also polls the job endpoints, so a dropped event costs freshness only.
 */
class ExerciseVariantJobServicePublishFailureTest {

    private static final String LOGIN = "instructor1";

    private ExerciseVariantJobService jobService;

    private VariantJob job;

    @BeforeEach
    void setUp() {
        HyperionWebsocketService websocketService = mock(HyperionWebsocketService.class);
        // The broker is unavailable / the payload cannot be converted — an unchecked throw that
        // HyperionWebsocketService does not catch itself (it only handles Interrupted/ExecutionException).
        doThrow(new IllegalStateException("broker unavailable")).when(websocketService).send(anyString(), anyString(), any());

        jobService = new ExerciseVariantJobService(new LocalDataProviderService(), websocketService);
        jobService.init();

        Exercise exercise = mock(Exercise.class);
        when(exercise.getId()).thenReturn(1L);
        when(exercise.getTitle()).thenReturn("Test Exercise");
        when(exercise.getExerciseType()).thenReturn(ExerciseType.PROGRAMMING);
        User user = mock(User.class);
        when(user.getLogin()).thenReturn(LOGIN);

        job = jobService.startJob(user, exercise, mock(VariantGenerationRequestDTO.class));
    }

    @Test
    void shouldKeepTheJobCompletedWhenTheDoneEventCannotBePublished() {
        assertThatCode(() -> jobService.complete(job.getJobId(), 42L, List.of())).doesNotThrowAnyException();

        VariantJob stored = jobService.getJob(job.getJobId(), LOGIN).orElseThrow();
        assertThat(stored.getPhase()).isEqualTo(VariantJobPhase.COMPLETED);
        // The deep link must survive: the variant exercise exists and this id is the only pointer to it.
        assertThat(stored.getVariantExerciseId()).isEqualTo(42L);
    }

    @Test
    void shouldStillReachTheTerminalPhaseWhenAFailureEventCannotBePublished() {
        assertThatCode(() -> jobService.fail(job.getJobId(), "Failed in VERIFYING")).doesNotThrowAnyException();

        assertThat(jobService.getJob(job.getJobId(), LOGIN).orElseThrow().getPhase()).isEqualTo(VariantJobPhase.FAILED);
    }

    @Test
    void shouldStillReachTheTerminalPhaseWhenACancellationEventCannotBePublished() {
        assertThatCode(() -> jobService.markCancelled(job.getJobId())).doesNotThrowAnyException();

        assertThat(jobService.getJob(job.getJobId(), LOGIN).orElseThrow().getPhase()).isEqualTo(VariantJobPhase.CANCELLED);
    }
}
