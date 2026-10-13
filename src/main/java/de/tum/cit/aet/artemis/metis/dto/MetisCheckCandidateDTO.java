package de.tum.cit.aet.artemis.metis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A graded individual participation whose effective due date has passed and that has no comprehension check yet, together with its student. A projection of
 * {@code MetisCheckRepository.findCheckCandidates}; no endpoint returns it.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisCheckCandidateDTO(long participationId, long studentId) {
}
