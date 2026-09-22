package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;

/**
 * The durable Weaviate write queue.
 * <p>
 * Rows are deleted once their write is confirmed, so this is always the backlog and never a history:
 * a healthy system shows a near-empty queue, and a growing {@code total} with a rising
 * {@code maxAttempts} is the signal that writes are failing and backing off.
 *
 * @param total          how many rows are waiting
 * @param dueNow         how many are past their backoff and would be picked up on the next drain
 * @param countsByOrigin how many rows each origin enqueued, separating live edits from repair work
 * @param oldestEnqueued when the oldest waiting row was enqueued, or null when the queue is empty
 * @param maxAttempts    the highest attempt count among waiting rows; above zero means writes are retrying
 * @param head           the rows that would be dispatched next, in dispatch order
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WeaviateOutboxQueueDTO(long total, long dueNow, Map<WeaviateOutboxOrigin, Long> countsByOrigin, @Nullable ZonedDateTime oldestEnqueued, int maxAttempts,
        List<OutboxEntryDTO> head) {
}
