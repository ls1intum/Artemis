package de.tum.cit.aet.artemis.lecture.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LectureUnitProcessingState#recordStageProgress}: what counts as progress for the
 * stall-detection clock.
 */
class LectureUnitProcessingStateTest {

    @Test
    void firstReportForAStageAlwaysAdvances() {
        LectureUnitProcessingState state = new LectureUnitProcessingState();

        boolean advanced = state.recordStageProgress("embedding", 1, 64);

        assertThat(advanced).isTrue();
        assertThat(state.getStageProgress()).isEqualTo(1);
        assertThat(state.getLastProgressAt()).isNotNull();
    }

    @Test
    void anIncreasingCounterAdvances() {
        LectureUnitProcessingState state = new LectureUnitProcessingState();
        state.recordStageProgress("embedding", 30, 64);

        boolean advanced = state.recordStageProgress("embedding", 31, 64);

        assertThat(advanced).isTrue();
        assertThat(state.getStageProgress()).isEqualTo(31);
    }

    @Test
    void anEqualCounterDoesNotAdvance() {
        LectureUnitProcessingState state = new LectureUnitProcessingState();
        state.recordStageProgress("embedding", 30, 64);

        boolean advanced = state.recordStageProgress("embedding", 30, 64);

        assertThat(advanced).isFalse();
    }

    @Test
    void aRegressingCounterDoesNotAdvance() {
        // An out-of-order or retried callback (e.g. 41, 40, 41) must not refresh the stall clock,
        // or a genuinely wedged stage could hide behind counter noise indefinitely.
        LectureUnitProcessingState state = new LectureUnitProcessingState();
        state.recordStageProgress("embedding", 41, 64);

        boolean advanced = state.recordStageProgress("embedding", 40, 64);

        assertThat(advanced).isFalse();
        // The stored counter still reflects whatever Iris most recently reported, for display purposes;
        // only the stall clock ignores the regression.
        assertThat(state.getStageProgress()).isEqualTo(40);
    }

    @Test
    void enteringANewStageAlwaysAdvancesRegardlessOfTheCounter() {
        LectureUnitProcessingState state = new LectureUnitProcessingState();
        state.recordStageProgress("embedding", 60, 64);

        boolean advanced = state.recordStageProgress("segment-summaries", 1, 6);

        assertThat(advanced).isTrue();
    }

    @Test
    void aCounterlessStageNeverAdvancesAfterItsFirstReport() {
        // No stageProgress at all (an audit stage, say): the first call still counts as progress
        // (entering the stage), but nothing thereafter can, since there is no counter to increase.
        LectureUnitProcessingState state = new LectureUnitProcessingState();

        boolean entered = state.recordStageProgress("audit", null, null);
        boolean repeated = state.recordStageProgress("audit", null, null);

        assertThat(entered).isTrue();
        assertThat(repeated).isFalse();
    }
}
