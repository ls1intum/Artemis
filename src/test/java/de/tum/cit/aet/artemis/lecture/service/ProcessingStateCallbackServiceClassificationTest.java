package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the static Pyris error-code classifier in {@link ProcessingStateCallbackService}.
 * The classifier is the single translation seam between raw Pyris {@code error_code} values and the
 * instructor-visible i18n key plus retryability flag stored on {@code LectureUnitProcessingState}.
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
}
