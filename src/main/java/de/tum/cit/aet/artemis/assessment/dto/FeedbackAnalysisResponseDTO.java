package de.tum.cit.aet.artemis.assessment.dto;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeedbackAnalysisResponseDTO(SearchResultPageDTO<FeedbackDetailDTO> feedbackDetails, long totalItems, Set<String> taskNames, List<String> testCaseNames,
        List<String> errorCategories, long highestOccurrenceOfGroupedFeedback) {
}
