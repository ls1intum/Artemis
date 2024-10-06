package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackDetailDTO(long count, double relativeCount, String detailText, String testCaseName, int taskNumber, String errorCategory) {
}
