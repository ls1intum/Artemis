package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Every queue this feature owns, and what each is doing right now.
 * <p>
 * Two genuinely different queues feed this view. The lecture ingestion queue is the
 * {@code lecture_unit_processing_state} table, which workers pull from; the Weaviate outbox is a
 * durable write queue drained on the scheduling node. They are reported side by side because an
 * operator debugging "why is nothing being indexed" has to look at both.
 *
 * @param lectureIngestion the pull-based lecture ingestion queue
 * @param weaviateOutbox   the durable Weaviate write queue
 * @param reconcilePasses  the periodic sweeps that enqueue repair work, and where each one is in its cycle
 * @param workers          the ingestion workers currently holding leases
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QueueOverviewDTO(LectureIngestionQueueDTO lectureIngestion, WeaviateOutboxQueueDTO weaviateOutbox, List<ReconcilePassStatusDTO> reconcilePasses,
        List<IngestionWorkerDTO> workers) {
}
