package de.tum.cit.aet.artemis.communication.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A page of visible FAQs, with the exact total for the import picker's paginator.
 *
 * @param resultsOnPage visible FAQs on the requested page
 * @param numberOfPages total number of matching pages
 * @param totalElements total number of matching visible FAQs
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqImportSearchResultDTO(List<FaqImportCandidateDTO> resultsOnPage, int numberOfPages, long totalElements) {
}
