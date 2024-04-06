package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.metis.PostSortCriterion;

/**
 * A DTO representing a filter for posts in a course or a conversation.
 *
 * @param courseId                  The id of the course
 * @param courseWideChannelIds      The ids of the course-wide channels
 * @param plagiarismCaseId          The id of the plagiarism case
 * @param conversationId            The id of the conversation
 * @param searchText                The text to search for
 * @param filterToUnresolved        Whether to filter to unresolved posts
 * @param filterToOwn               Whether to filter to own posts
 * @param filterToAnsweredOrReacted Whether to filter to answered or reacted posts
 * @param postSortCriterion         The criterion to sort the posts by
 * @param sortingOrder              The order to sort the posts in
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PostContextFilter(long courseId, long[] courseWideChannelIds, Long plagiarismCaseId, Long conversationId, String searchText, boolean filterToUnresolved,
        boolean filterToOwn, boolean filterToAnsweredOrReacted, PostSortCriterion postSortCriterion, SortingOrder sortingOrder) {

    public static PostContextFilter of(long courseId) {
        return new PostContextFilter(courseId, null, null, null, null, false, false, false, null, null);
    }

    public static PostContextFilter of(long courseId, long[] courseWideChannelIds) {
        return new PostContextFilter(courseId, courseWideChannelIds, null, null, null, false, false, false, null, null);
    }

    public static PostContextFilter of(long courseId, long conversationId) {
        return new PostContextFilter(courseId, null, null, conversationId, null, false, false, false, null, null);
    }
}
