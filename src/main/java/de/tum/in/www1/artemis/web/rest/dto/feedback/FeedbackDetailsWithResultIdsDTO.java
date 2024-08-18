package de.tum.in.www1.artemis.web.rest.dto.feedback;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackDetailsWithResultIdsDTO(List<FeedbackDetailDTO> feedbackDetails, List<Long> resultIds) {
}
