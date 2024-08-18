package de.tum.in.www1.artemis.web.rest.dto.feedback;

import java.util.List;

public record FeedbackDetailsWithResultIdsDTO(List<FeedbackDetailDTO> feedbackDetails, List<Long> resultIds) {
}
