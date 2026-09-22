package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;

/**
 * The pull-based lecture ingestion queue: how deep it is, what is running, and what is next.
 *
 * @param countsByPhase how many units sit in each {@link ProcessingPhase}
 * @param running       the units a worker currently holds, with their live stage and progress
 * @param nextUp        the units that would be claimed next, in the order the dispatcher would hand them out
 * @param retryWaiting  how many failed units are waiting out their retry backoff
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureIngestionQueueDTO(Map<ProcessingPhase, Long> countsByPhase, List<RunningIngestionDTO> running, List<QueuedIngestionDTO> nextUp, long retryWaiting) {
}
