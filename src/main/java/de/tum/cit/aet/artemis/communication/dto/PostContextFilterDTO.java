package de.tum.cit.aet.artemis.communication.dto;

import java.util.Arrays;
import java.util.Objects;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.PostSortCriterion;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PostContextFilterDTO(@NotBlank Long courseId, Long plagiarismCaseId, long[] conversationIds, long[] authorIds, String searchText, Boolean filterToCourseWide,
        Boolean filterToUnresolved, Boolean filterToAnsweredOrReacted, PostSortCriterion postSortCriterion, SortingOrder sortingOrder, Boolean pinnedOnly,
        Boolean filterToUnverifiedIris) {

    // Overloaded constructor to set pinnedOnly and filterToUnverifiedIris to false by default if not provided.
    public PostContextFilterDTO(@NotBlank Long courseId, Long plagiarismCaseId, long[] conversationIds, long[] authorIds, String searchText, Boolean filterToCourseWide,
            Boolean filterToUnresolved, Boolean filterToAnsweredOrReacted, PostSortCriterion postSortCriterion, SortingOrder sortingOrder) {
        this(courseId, plagiarismCaseId, conversationIds, authorIds, searchText, filterToCourseWide, filterToUnresolved, filterToAnsweredOrReacted, postSortCriterion, sortingOrder,
                false, false);
    }

    public PostContextFilterDTO(@NotBlank Long courseId, Long plagiarismCaseId, long[] conversationIds, long[] authorIds, String searchText, Boolean filterToCourseWide,
            Boolean filterToUnresolved, Boolean filterToAnsweredOrReacted, PostSortCriterion postSortCriterion, SortingOrder sortingOrder, Boolean pinnedOnly) {
        this(courseId, plagiarismCaseId, conversationIds, authorIds, searchText, filterToCourseWide, filterToUnresolved, filterToAnsweredOrReacted, postSortCriterion, sortingOrder,
                pinnedOnly, false);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PostContextFilterDTO that)) {
            return false;
        }
        return Objects.equals(courseId, that.courseId) && Objects.equals(plagiarismCaseId, that.plagiarismCaseId) && Arrays.equals(conversationIds, that.conversationIds)
                && Arrays.equals(authorIds, that.authorIds) && Objects.equals(searchText, that.searchText) && Objects.equals(filterToCourseWide, that.filterToCourseWide)
                && Objects.equals(filterToUnresolved, that.filterToUnresolved) && Objects.equals(filterToAnsweredOrReacted, that.filterToAnsweredOrReacted)
                && postSortCriterion == that.postSortCriterion && sortingOrder == that.sortingOrder && Objects.equals(pinnedOnly, that.pinnedOnly)
                && Objects.equals(filterToUnverifiedIris, that.filterToUnverifiedIris);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courseId, plagiarismCaseId, Arrays.hashCode(conversationIds), Arrays.hashCode(authorIds), searchText, filterToCourseWide, filterToUnresolved,
                filterToAnsweredOrReacted, postSortCriterion, sortingOrder, pinnedOnly, filterToUnverifiedIris);
    }

    @Override
    public String toString() {
        return "PostContextFilterDTO[courseId=" + courseId + ", plagiarismCaseId=" + plagiarismCaseId + ", conversationIds=" + Arrays.toString(conversationIds) + ", authorIds="
                + Arrays.toString(authorIds) + ", searchText=" + searchText + ", filterToCourseWide=" + filterToCourseWide + ", filterToUnresolved=" + filterToUnresolved
                + ", filterToAnsweredOrReacted=" + filterToAnsweredOrReacted + ", postSortCriterion=" + postSortCriterion + ", sortingOrder=" + sortingOrder + ", pinnedOnly="
                + pinnedOnly + ", filterToUnverifiedIris=" + filterToUnverifiedIris + "]";
    }
}
