package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.service.DeletionProgressTracker;
import de.tum.cit.aet.artemis.exercise.service.DeletionStepReporter;

class DeletionStepReporterTest {

    @Test
    void stepStartedAfterDeletion_doesNotFailIfTheProgressCannotBeReported() {
        DeletionProgressTracker tracker = mock(DeletionProgressTracker.class);
        doThrow(new IllegalStateException("the claim was lost")).when(tracker).startStep("cleanup");
        var reporter = new DeletionStepReporter(tracker, Map.of());

        // The entity is deleted already, the cleanup that follows must not be skipped because a progress message failed.
        assertThatCode(() -> reporter.stepStartedAfterDeletion("cleanup")).doesNotThrowAnyException();
        verify(tracker).startStep("cleanup");
    }

    @Test
    void stepStarted_keepsFailingIfTheProgressCannotBeReported() {
        DeletionProgressTracker tracker = mock(DeletionProgressTracker.class);
        doThrow(new IllegalStateException("the claim was lost")).when(tracker).startStep("participations");
        var reporter = new DeletionStepReporter(tracker, Map.of());

        // Before anything is deleted, a lost claim has to stop the deletion.
        assertThatThrownBy(() -> reporter.stepStarted("participations")).isInstanceOf(IllegalStateException.class).hasMessageContaining("claim was lost");
    }

    @Test
    void claimExercise_isForwardedToTheTrackerAndIgnoredWithoutOne() {
        DeletionProgressTracker tracker = mock(DeletionProgressTracker.class);
        new DeletionStepReporter(tracker, Map.of()).claimExercise(42L);
        verify(tracker).claimExercise(42L);

        assertThatCode(() -> DeletionStepReporter.none().claimExercise(42L)).doesNotThrowAnyException();
    }
}
