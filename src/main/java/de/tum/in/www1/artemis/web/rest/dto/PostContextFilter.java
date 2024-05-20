package de.tum.in.www1.artemis.web.rest.dto;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.metis.PostSortCriterion;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PostContextFilter(@NotBlank Long courseId, long[] courseWideChannelIds, Long plagiarismCaseId, Long conversationId, String searchText, Boolean filterToUnresolved,
        Boolean filterToOwn, Boolean filterToAnsweredOrReacted, PostSortCriterion postSortCriterion, SortingOrder sortingOrder) {

    /**
     * This is an instance initializer block for the PostContextFilter record.
     * It checks if the Boolean values filterToUnresolved, filterToOwn, and filterToAnsweredOrReacted are null.
     * If they are, it assigns them a default value of false.
     */
    public PostContextFilter {
        if (filterToUnresolved == null) {
            filterToUnresolved = false;
        }
        if (filterToOwn == null) {
            filterToOwn = false;
        }
        if (filterToAnsweredOrReacted == null) {
            filterToAnsweredOrReacted = false;
        }
    }
}
