package de.tum.cit.aet.artemis.metis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A comprehension check together with the student whose code it is about, so that a consent sweep can look up all students of a batch of checks at once. A
 * projection of {@code MetisCheckRepository.findSkippedChecksWithReason}; no endpoint returns it.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisCheckOwnerDTO(long checkId, long studentId) {
}
