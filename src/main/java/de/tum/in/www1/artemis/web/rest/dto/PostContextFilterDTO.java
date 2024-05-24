package de.tum.in.www1.artemis.web.rest.dto;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.metis.PostSortCriterion;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PostContextFilterDTO(@NotBlank Long courseId, long[] courseWideChannelIds, Long plagiarismCaseId, Long conversationId, String searchText, Boolean filterToUnresolved,
        Boolean filterToOwn, Boolean filterToAnsweredOrReacted, PostSortCriterion postSortCriterion, SortingOrder sortingOrder) {
}
