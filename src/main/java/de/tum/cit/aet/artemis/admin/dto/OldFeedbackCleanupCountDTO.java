package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO previewing the entities that would be deleted by the age-based "delete feedback of non-latest results" cleanup for
 * courses that ended before the configured cutoff. Combines the rated and non-rated variants; only the feedback (feedback
 * rows, long feedback texts, text blocks) of non-latest results is removed, never the results themselves.
 *
 * @param longFeedbackText the number of long feedback texts that would be deleted
 * @param textBlock        the number of text blocks that would be deleted
 * @param feedback         the number of feedback rows that would be deleted
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OldFeedbackCleanupCountDTO(int longFeedbackText, int textBlock, int feedback) {
}
