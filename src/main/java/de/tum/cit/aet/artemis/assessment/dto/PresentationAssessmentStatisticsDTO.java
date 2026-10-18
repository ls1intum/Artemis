package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Course-wide counts of individual presentation assessment instances.
 *
 * @param totalCount    the number of all instances
 * @param assessedCount the number of instances with assigned result points
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentStatisticsDTO(long totalCount, long assessedCount) {
}
