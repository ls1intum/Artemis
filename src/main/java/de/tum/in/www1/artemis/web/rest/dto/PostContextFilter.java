package de.tum.in.www1.artemis.web.rest.dto;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.metis.PostSortCriterion;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PostContextFilter(@NotBlank Long courseId, long[] courseWideChannelIds, Long plagiarismCaseId, Long conversationId, String searchText, boolean filterToUnresolved,
        boolean filterToOwn, boolean filterToAnsweredOrReacted, PostSortCriterion postSortCriterion, SortingOrder sortingOrder) {

    /**
     * Constructor for PostContextFilter, which sets every member as null, except boolean members and courseId
     *
     * @param courseId id of the course that the posts belong to
     */
    public PostContextFilter(long courseId) {
        this(courseId, null, null, null, null, false, false, false, null, null);
    }
}
