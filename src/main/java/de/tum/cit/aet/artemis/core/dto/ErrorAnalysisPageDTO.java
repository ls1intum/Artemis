package de.tum.cit.aet.artemis.core.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One page of the groups of the error analysis, together with the totals over all pages.
 * <p>
 * The groups are sorted before they are cut into pages, so the pages follow the order that was asked for.
 *
 * @param groups      the groups of the requested page
 * @param totalGroups how many groups there are on all pages
 * @param totalErrors how many errors there are in all groups on all pages
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorAnalysisPageDTO(List<ErrorGroupDTO> groups, long totalGroups, long totalErrors) {
}
