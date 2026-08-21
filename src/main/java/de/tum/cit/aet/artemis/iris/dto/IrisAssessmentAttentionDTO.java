package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Indicates whether an Iris assessment requires the instructor's attention.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisAssessmentAttentionDTO(boolean needsAttention) {
}
