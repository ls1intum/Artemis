package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackAnalysisResponseDTO(SearchResultPageDTO<FeedbackDetailDTO> feedbackDetails, long distinctResultCount) {
}
