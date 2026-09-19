package de.tum.cit.aet.artemis.lecture.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

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
        // The stored counter stays at the last genuinely-advanced value: overwriting it with the regressed
        // report would shift the baseline the next call compares against (see the test below).
        assertThat(state.getStageProgress()).isEqualTo(41);
    }

    @Test
    void repeatingAnAlreadySeenPeakAfterARegressionDoesNotAdvance() {
        // 41, 40, 41: the regression at 40 is stored for display (see the test above), but that must not
        // shift the baseline the stall clock compares against. The third call repeats a value already seen
        // at the first call, so it must not count as progress even though 41 > 40.
        LectureUnitProcessingState state = new LectureUnitProcessingState();
        state.recordStageProgress("embedding", 41, 64);
        state.recordStageProgress("embedding", 40, 64);
        ZonedDateTime lastProgressAfterRegression = state.getLastProgressAt();

        boolean advanced = state.recordStageProgress("embedding", 41, 64);

        assertThat(advanced).isFalse();
        assertThat(state.getLastProgressAt()).isEqualTo(lastProgressAfterRegression);
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
