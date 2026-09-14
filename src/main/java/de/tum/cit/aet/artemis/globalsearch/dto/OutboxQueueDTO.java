package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The outbox queue as shown by the ingestion dashboard: a bounded head of the queue plus the true total, so a
 * backlog larger than the page still reports its real size.
 *
 * @param totalDepth how many rows the outbox holds in total
 * @param entries    the head of the queue in dispatch order, capped by the endpoint's page size
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OutboxQueueDTO(long totalDepth, List<OutboxQueueEntryDTO> entries) {
}
