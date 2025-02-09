package de.tum.cit.aet.artemis.communication.dto;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.PostSortCriterion;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PostContextFilterDTO(@NotBlank Long courseId, long[] courseWideChannelIds, Long plagiarismCaseId, Long conversationId, String searchText, Boolean filterToUnresolved,
        Boolean filterToOwn, Boolean filterToAnsweredOrReacted, PostSortCriterion postSortCriterion, SortingOrder sortingOrder, Boolean pinnedOnly) {

    // Overloaded constructor to set pinnedOnly to false by default if not provided.
    public PostContextFilterDTO(@NotBlank Long courseId, long[] courseWideChannelIds, Long plagiarismCaseId, Long conversationId, String searchText, Boolean filterToUnresolved,
            Boolean filterToOwn, Boolean filterToAnsweredOrReacted, PostSortCriterion postSortCriterion, SortingOrder sortingOrder) {
        this(courseId, courseWideChannelIds, plagiarismCaseId, conversationId, searchText, filterToUnresolved, filterToOwn, filterToAnsweredOrReacted, postSortCriterion,
                sortingOrder, false);
    }
}
