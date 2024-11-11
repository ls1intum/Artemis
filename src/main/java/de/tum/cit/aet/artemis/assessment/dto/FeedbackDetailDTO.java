package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackDetailDTO(String concatenatedFeedbackIds, long count, double relativeCount, String detailText, String testCaseName, String taskName, String errorCategory) {
}
