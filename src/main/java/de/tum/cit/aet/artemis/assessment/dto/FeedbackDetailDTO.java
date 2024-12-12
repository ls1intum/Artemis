package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackDetailDTO(List<Long> concatenatedFeedbackIds, long count, double relativeCount, String detailText, String testCaseName, String taskName,
        String errorCategory) {

    public FeedbackDetailDTO(String concatenatedFeedbackIds, long count, double relativeCount, String detailText, String testCaseName, String taskName, String errorCategory) {
        this(Arrays.stream(concatenatedFeedbackIds.split(",")).map(Long::valueOf).toList(), count, relativeCount, detailText, testCaseName, taskName, errorCategory);
    }

}
