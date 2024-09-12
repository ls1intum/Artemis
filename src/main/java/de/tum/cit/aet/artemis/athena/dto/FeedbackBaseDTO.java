package de.tum.cit.aet.artemis.athena.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Interface used to type the FeedbackDTOs for Athena: ProgrammingFeedbackDTO and TextFeedbackDTO
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface FeedbackBaseDTO {
}
