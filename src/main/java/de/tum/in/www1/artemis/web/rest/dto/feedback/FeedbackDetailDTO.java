package de.tum.in.www1.artemis.web.rest.dto.feedback;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackDetailDTO(String detailText, String testCaseName) {
}
