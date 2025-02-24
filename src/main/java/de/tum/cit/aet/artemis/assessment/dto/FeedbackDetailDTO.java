package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackDetailDTO(List<Long> feedbackIds, long count, double relativeCount, List<String> detailTexts, String testCaseName, String taskName, String errorCategory,
        boolean hasLongFeedbackText) {

    public FeedbackDetailDTO(String feedbackId, long count, double relativeCount, String detailText, String testCaseName, String taskName, String errorCategory,
            boolean hasLongFeedbackText) {
        // Feedback IDs are gathered in the query using a comma separator, and the detail texts are stored in a list because, in case aggregation is applied, the detail texts are
        // grouped together
        this(Arrays.stream(feedbackId.split(",")).map(Long::valueOf).toList(), count, relativeCount, List.of(detailText), testCaseName, taskName, errorCategory,
                hasLongFeedbackText);
    }
}
