package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;

/**
 * Unit tests for the static seams in {@link ProcessingStateCallbackService}.
 * <p>
 * The Pyris error-code classifier is the single translation seam between raw Pyris {@code error_code} values and the
 * instructor-visible i18n key plus retryability flag stored on {@code LectureUnitProcessingState}. The transcription
 * version bump is the single place where the version an Iris citation pins a video timestamp to is advanced.
 */
class ProcessingStateCallbackServiceClassificationTest {

    @Test
    void nullCodeMapsToGenericKeyAndRetryable() {
        var result = ProcessingStateCallbackService.classifyIngestionFailure(null);
        assertThat(result.errorKey()).isEqualTo("artemisApp.attachmentVideoUnit.processing.error.processingFailed");
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void blankCodeMapsToGenericKeyAndRetryable() {
        var result = ProcessingStateCallbackService.classifyIngestionFailure("   ");
        assertThat(result.errorKey()).isEqualTo("artemisApp.attachmentVideoUnit.processing.error.processingFailed");
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void unknownCodeMapsToGenericKeyAndRetryable() {
        var result = ProcessingStateCallbackService.classifyIngestionFailure("SOMETHING_NEW_FROM_PYRIS");
        assertThat(result.errorKey()).isEqualTo("artemisApp.attachmentVideoUnit.processing.error.processingFailed");
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void youtubePrivateIsPermanent() {
        var result = ProcessingStateCallbackService.classifyIngestionFailure("YOUTUBE_PRIVATE");
        assertThat(result.errorKey()).isEqualTo("artemisApp.attachmentVideoUnit.processing.error.youtubePrivate");
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void youtubeLiveIsPermanent() {
        var result = ProcessingStateCallbackService.classifyIngestionFailure("YOUTUBE_LIVE");
        assertThat(result.errorKey()).isEqualTo("artemisApp.attachmentVideoUnit.processing.error.youtubeLive");
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void youtubeTooLongIsPermanent() {
        var result = ProcessingStateCallbackService.classifyIngestionFailure("YOUTUBE_TOO_LONG");
        assertThat(result.errorKey()).isEqualTo("artemisApp.attachmentVideoUnit.processing.error.youtubeTooLong");
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void youtubeUnavailableIsPermanent() {
        var result = ProcessingStateCallbackService.classifyIngestionFailure("YOUTUBE_UNAVAILABLE");
        assertThat(result.errorKey()).isEqualTo("artemisApp.attachmentVideoUnit.processing.error.youtubeUnavailable");
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void youtubeDownloadFailedIsTransient() {
        var result = ProcessingStateCallbackService.classifyIngestionFailure("YOUTUBE_DOWNLOAD_FAILED");
        assertThat(result.errorKey()).isEqualTo("artemisApp.attachmentVideoUnit.processing.error.youtubeDownloadFailed");
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void firstTranscriptionStartsAtVersionOne() {
        var state = processingState();

        ProcessingStateCallbackService.bumpTranscriptionVersionIfContentChanged(state, List.of(segment(0, 10, "Hello")));

        assertThat(state.getTranscriptionVersion()).isEqualTo(1);
        assertThat(state.getTranscriptionContentHash()).isNotBlank();
    }

    @Test
    void repeatedCheckpointsWithIdenticalSegmentsDoNotInflateTheVersion() {
        var state = processingState();
        var segments = List.of(segment(0, 10, "Hello"), segment(10, 20, "World"));

        ProcessingStateCallbackService.bumpTranscriptionVersionIfContentChanged(state, segments);
        ProcessingStateCallbackService.bumpTranscriptionVersionIfContentChanged(state, segments);
        ProcessingStateCallbackService.bumpTranscriptionVersionIfContentChanged(state, List.copyOf(segments));

        assertThat(state.getTranscriptionVersion()).isEqualTo(1);
    }

    @Test
    void changedSegmentsIncrementTheVersion() {
        var state = processingState();

        ProcessingStateCallbackService.bumpTranscriptionVersionIfContentChanged(state, List.of(segment(0, 10, "Hello")));
        ProcessingStateCallbackService.bumpTranscriptionVersionIfContentChanged(state, List.of(segment(0, 10, "Hello there")));

        assertThat(state.getTranscriptionVersion()).isEqualTo(2);
    }

    @Test
    void shiftedTimestampsIncrementTheVersionEvenWhenTheTextIsIdentical() {
        var state = processingState();

        ProcessingStateCallbackService.bumpTranscriptionVersionIfContentChanged(state, List.of(segment(0, 10, "Hello")));
        ProcessingStateCallbackService.bumpTranscriptionVersionIfContentChanged(state, List.of(segment(2, 12, "Hello")));

        assertThat(state.getTranscriptionVersion()).isEqualTo(2);
    }

    private static LectureUnitProcessingState processingState() {
        var unit = new AttachmentVideoUnit();
        unit.setId(1L);
        return new LectureUnitProcessingState(unit);
    }

    private static LectureTranscriptionSegment segment(double startTime, double endTime, String text) {
        return new LectureTranscriptionSegment(startTime, endTime, text, 1);
    }
}
