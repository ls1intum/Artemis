package de.tum.cit.aet.artemis.communication.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A page of visible FAQs, with the exact total for the import picker's paginator. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqImportSearchResultDTO(List<FaqImportCandidateDTO> resultsOnPage, int numberOfPages, long totalElements) {
}
