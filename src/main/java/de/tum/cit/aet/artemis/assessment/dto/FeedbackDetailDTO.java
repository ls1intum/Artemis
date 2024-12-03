package de.tum.cit.aet.artemis.assessment.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackDetailDTO(long count, double relativeCount, List<String> detailTexts, String testCaseName, String taskName, String errorCategory) {

    public FeedbackDetailDTO(long count, double relativeCount, String detailText, String testCaseName, String taskName, String errorCategory) {
        this(count, relativeCount, List.of(detailText), testCaseName, taskName, errorCategory);
    }
}
