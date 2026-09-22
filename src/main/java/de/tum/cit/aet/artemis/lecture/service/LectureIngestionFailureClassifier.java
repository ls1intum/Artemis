package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_PROCESSING_RETRIES;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;

/**
 * Computes the field changes a lecture-ingestion failure applies (retry count, i18n error key,
 * backoff), and classifies a raw Pyris error code into that i18n key plus a retryability flag.
 * Pure and stateless: {@link ProcessingStateCallbackService} commits the result through whichever
 * storage path (unconditional save or claim-checked atomic update) its caller needs.
 */
final class LectureIngestionFailureClassifier {

    private static final Logger log = LoggerFactory.getLogger(LectureIngestionFailureClassifier.class);

    private LectureIngestionFailureClassifier() {
    }

    /**
     * Compute the field changes a failure applies to {@code state} (retry count, error key, backoff),
     * without persisting them, so both the unconditional and the claim-checked failure writers commit
     * the exact same values through their own storage path.
     */
    static FailureComputation computeFailure(LectureUnitProcessingState state, @Nullable String errorCode) {
        ZonedDateTime now = ZonedDateTime.now();
        state.incrementRetryCount();
        state.setIngestionJobToken(null);
        // Undo the dispatch attempt, including the claim that started it: startedAt is what marks a job as taken, so
        // leaving it set would keep this unit out of the idle queue for good.
        state.setStartedAt(null);

        ProcessingErrorClassification classification = classifyIngestionFailure(errorCode);
        state.markFailed(classification.errorKey());

        boolean maxRetriesReached = state.getRetryCount() >= MAX_PROCESSING_RETRIES;
        if (maxRetriesReached || !classification.retryable()) {
            if (!classification.retryable()) {
                log.warn("Unit {} failed with permanent error code '{}', no retry scheduled", state.getLectureUnit().getId(), errorCode);
            }
            else {
                log.warn("Max retries reached for unit {}, marking as permanently failed", state.getLectureUnit().getId());
            }
            return new FailureComputation(state.getRetryCount(), classification.errorKey(), null, null, now);
        }

        long backoffMinutes = calculateBackoffMinutes(state.getRetryCount());
        state.scheduleRetry(backoffMinutes);
        return new FailureComputation(state.getRetryCount(), classification.errorKey(), state.getRetryEligibleAt(), backoffMinutes, now);
    }

    /**
     * The field changes a failure applies, computed once and shared by both failure writers.
     *
     * @param retryCount      the new retry count
     * @param errorKey        the i18n error key to persist
     * @param retryEligibleAt when the retry becomes eligible, or {@code null} for a permanent failure
     * @param backoffMinutes  the backoff applied, or {@code null} for a permanent failure (log-only, not persisted)
     * @param now             the timestamp this computation was performed at
     */
    record FailureComputation(int retryCount, String errorKey, @Nullable ZonedDateTime retryEligibleAt, @Nullable Long backoffMinutes, ZonedDateTime now) {
    }

    /**
     * Translate a raw Pyris {@code error_code} into a specific, instructor-readable i18n key plus a
     * retryability flag. Unknown and blank codes fall back to the generic key with retryable = true.
     */
    static ProcessingErrorClassification classifyIngestionFailure(@Nullable String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return new ProcessingErrorClassification("artemisApp.attachmentVideoUnit.processing.error.processingFailed", true);
        }
        return switch (rawCode) {
            case "YOUTUBE_PRIVATE" -> new ProcessingErrorClassification("artemisApp.attachmentVideoUnit.processing.error.youtubePrivate", false);
            case "YOUTUBE_LIVE" -> new ProcessingErrorClassification("artemisApp.attachmentVideoUnit.processing.error.youtubeLive", false);
            case "YOUTUBE_TOO_LONG" -> new ProcessingErrorClassification("artemisApp.attachmentVideoUnit.processing.error.youtubeTooLong", false);
            case "YOUTUBE_UNAVAILABLE" -> new ProcessingErrorClassification("artemisApp.attachmentVideoUnit.processing.error.youtubeUnavailable", false);
            case "YOUTUBE_DOWNLOAD_FAILED" -> new ProcessingErrorClassification("artemisApp.attachmentVideoUnit.processing.error.youtubeDownloadFailed", true);
            default -> new ProcessingErrorClassification("artemisApp.attachmentVideoUnit.processing.error.processingFailed", true);
        };
    }

    /**
     * Result of classifying a raw Pyris failure code at the state-write boundary.
     *
     * @param errorKey  i18n key stored on the processing state; shown to instructors via the status tooltip
     * @param retryable whether the failure is eligible for automatic retry (permanent input errors are not)
     */
    record ProcessingErrorClassification(String errorKey, boolean retryable) {
    }

    /**
     * Calculate exponential backoff delay in minutes.
     * Formula: 2^retryCount minutes (2, 4, 8, 16, 32 minutes for retries 1-5).
     *
     * @param retryCount current retry attempt number
     * @return backoff delay in minutes
     */
    static long calculateBackoffMinutes(int retryCount) {
        return (long) Math.pow(2, retryCount);
    }
}
