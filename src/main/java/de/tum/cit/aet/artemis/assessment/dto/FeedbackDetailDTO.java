package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackDetailDTO(List<Long> feedbackIds, long count, double relativeCount, List<String> detailTexts, String testCaseName, String taskName, String errorCategory) {

    public FeedbackDetailDTO(String feedbackId, long count, double relativeCount, String detailText, String testCaseName, String taskName, String errorCategory) {
        this(Arrays.stream(feedbackId.split(",")).map(Long::valueOf).toList(), count, relativeCount, List.of(detailText), testCaseName, taskName, errorCategory);
    }
}
