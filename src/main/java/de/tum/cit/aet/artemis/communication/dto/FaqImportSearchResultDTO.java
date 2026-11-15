package de.tum.cit.aet.artemis.communication.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A page of visible FAQs, with the exact total for the import picker's paginator.
 *
 * @param resultsOnPage visible FAQs on the requested page, at most ten, omitted when the page is empty
 * @param numberOfPages total number of matching pages, also sent when zero
 * @param totalElements total number of matching visible FAQs, also sent when zero
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqImportSearchResultDTO(@Nullable List<FaqImportCandidateDTO> resultsOnPage, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int numberOfPages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements) {
}
