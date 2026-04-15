package de.tum.cit.aet.artemis.lecture.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LectureUnitProcessingStateErrorCodeTest {

    @Test
    void errorCodeDefaultsToNull() {
        var state = new LectureUnitProcessingState();
        assertThat(state.getErrorCode()).isNull();
    }

    @Test
    void errorCodeSetterPersistsValue() {
        var state = new LectureUnitProcessingState();
        state.setErrorCode("YOUTUBE_LIVE");
        assertThat(state.getErrorCode()).isEqualTo("YOUTUBE_LIVE");
    }

    @Test
    void transitionToClearsErrorCode() {
        // transitionTo() clears errorKey — it must also clear errorCode
        var state = new LectureUnitProcessingState();
        state.setErrorKey("some.key");
        state.setErrorCode("YOUTUBE_LIVE");
        state.transitionTo(ProcessingPhase.TRANSCRIBING);
        assertThat(state.getErrorKey()).isNull();
        assertThat(state.getErrorCode()).isNull();
    }

    @Test
    void markFailedSetsErrorCode() {
        // markFailed() sets errorKey — it must also set errorCode when provided
        var state = new LectureUnitProcessingState();
        state.markFailed("artemisApp.processing.error.transcriptionFailed", "TRANSCRIPTION_FAILED");
        assertThat(state.getErrorKey()).isEqualTo("artemisApp.processing.error.transcriptionFailed");
        assertThat(state.getErrorCode()).isEqualTo("TRANSCRIPTION_FAILED");
    }

    @Test
    void markFailedWithNullErrorCode() {
        // Callers may pass null for errorCode — ensure no NPE and field clears
        var state = new LectureUnitProcessingState();
        state.setErrorCode("YOUTUBE_LIVE");
        state.markFailed("some.key", null);
        assertThat(state.getErrorCode()).isNull();
    }
}
