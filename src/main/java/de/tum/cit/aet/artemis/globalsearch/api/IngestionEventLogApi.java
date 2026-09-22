package de.tum.cit.aet.artemis.globalsearch.api;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionEventKind;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionEventLogService;

/**
 * API for the lecture module to record ingestion pipeline events into the global-search activity log.
 * <p>
 * The log lives in the global-search module because that is where the admin dashboard reading it lives,
 * but the lecture ingestion run is what produces most of its events. This facade is the module boundary
 * between the two; see {@link IngestionEventLogService} for the best-effort write contract.
 */
@Conditional(WeaviateEnabled.class)
@Controller
@Lazy
public class IngestionEventLogApi extends AbstractGlobalSearchApi {

    private final IngestionEventLogService ingestionEventLogService;

    public IngestionEventLogApi(IngestionEventLogService ingestionEventLogService) {
        this.ingestionEventLogService = ingestionEventLogService;
    }

    /**
     * Records that a lecture unit finished ingesting successfully.
     *
     * @param lectureUnitId the unit that finished
     * @param courseId      the course it belongs to, or null when not resolved
     * @param detail        short human-readable context, such as the quality score
     */
    public void recordIngested(long lectureUnitId, @Nullable Long courseId, @Nullable String detail) {
        ingestionEventLogService.record(IngestionEventKind.INGESTED, "LectureUnit", lectureUnitId, courseId, detail);
    }

    /**
     * Records that an ingestion run failed.
     *
     * @param lectureUnitId the unit that failed
     * @param courseId      the course it belongs to, or null when not resolved
     * @param detail        short human-readable context, such as the error key and stage
     */
    public void recordFailed(long lectureUnitId, @Nullable Long courseId, @Nullable String detail) {
        ingestionEventLogService.record(IngestionEventKind.FAILED, "LectureUnit", lectureUnitId, courseId, detail);
    }

    /**
     * Records that a worker claimed a unit and started working on it.
     *
     * @param lectureUnitId the claimed unit
     * @param courseId      the course it belongs to, or null when not resolved
     * @param detail        short human-readable context, such as the claiming worker's boot id
     */
    public void recordClaimed(long lectureUnitId, @Nullable Long courseId, @Nullable String detail) {
        ingestionEventLogService.record(IngestionEventKind.CLAIMED, "LectureUnit", lectureUnitId, courseId, detail);
    }

    /**
     * Records that a run went back on the queue without a terminal failure.
     *
     * @param lectureUnitId the requeued unit
     * @param courseId      the course it belongs to, or null when not resolved
     * @param detail        short human-readable context, such as why it was requeued
     */
    public void recordRequeued(long lectureUnitId, @Nullable Long courseId, @Nullable String detail) {
        ingestionEventLogService.record(IngestionEventKind.REQUEUED, "LectureUnit", lectureUnitId, courseId, detail);
    }

    /**
     * Records that a run stopped making progress while its worker kept heartbeating.
     *
     * @param lectureUnitId the wedged unit
     * @param courseId      the course it belongs to, or null when not resolved
     * @param detail        short human-readable context, such as the frozen stage
     */
    public void recordStalled(long lectureUnitId, @Nullable Long courseId, @Nullable String detail) {
        ingestionEventLogService.record(IngestionEventKind.STALLED, "LectureUnit", lectureUnitId, courseId, detail);
    }

    /**
     * Records that a unit was re-ingested because its stored content scored below the quality threshold.
     *
     * @param lectureUnitId the flagged unit
     * @param courseId      the course it belongs to, or null when not resolved
     * @param detail        short human-readable context, such as the score and threshold
     */
    public void recordQualityFlagged(long lectureUnitId, @Nullable Long courseId, @Nullable String detail) {
        ingestionEventLogService.record(IngestionEventKind.QUALITY_FLAGGED, "LectureUnit", lectureUnitId, courseId, detail);
    }
}
