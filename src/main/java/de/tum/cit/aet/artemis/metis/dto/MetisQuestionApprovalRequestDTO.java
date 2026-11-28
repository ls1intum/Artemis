package de.tum.cit.aet.artemis.metis.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A main question as an editor saw it when they approved it during the review: its text and how often it was generated again. The approval holds only while
 * the question is still the one the editor read, so another editor's regeneration or change of the text in between is never approved unread. Both fields are
 * required: the count is boxed, so that a request without it fails validation instead of reading as 0.
 *
 * @param questionText      the text the editor saw
 * @param regenerationCount how often the question had been generated again when the editor saw it
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisQuestionApprovalRequestDTO(@NotBlank @Size(min = 1, max = 2000) String questionText, @NotNull @Min(0) Integer regenerationCount) {
}
