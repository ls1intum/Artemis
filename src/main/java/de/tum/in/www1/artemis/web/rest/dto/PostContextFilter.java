package de.tum.in.www1.artemis.web.rest.dto;

import javax.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.metis.CourseWideContext;
import de.tum.in.www1.artemis.domain.metis.PostSortCriterion;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PostContextFilter {

    @NotBlank
    private Long courseId;

    private CourseWideContext[] courseWideContexts;

    private Long[] exerciseIds;

    private Long[] lectureIds;

    private Long plagiarismCaseId;

    private Long conversationId;

    private String searchText;

    private boolean filterToUnresolved = false;

    private boolean filterToOwn = false;

    private boolean filterToAnsweredOrReacted = false;

    private PostSortCriterion postSortCriterion;

    private SortingOrder sortingOrder;

    /**
     * Constructor for PostContextFilter, which sets every member as null, except boolean members and courseId
     *
     * @param courseId id of the course that the posts belong to
     */
    public PostContextFilter(long courseId) {
        this.courseId = courseId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public CourseWideContext[] getCourseWideContexts() {
        return courseWideContexts;
    }

    public void setCourseWideContexts(CourseWideContext[] courseWideContexts) {
        this.courseWideContexts = courseWideContexts;
    }

    public Long[] getExerciseIds() {
        return exerciseIds;
    }

    public void setExerciseIds(Long[] exerciseIds) {
        this.exerciseIds = exerciseIds;
    }

    public Long[] getLectureIds() {
        return lectureIds;
    }

    public void setLectureIds(Long[] lectureIds) {
        this.lectureIds = lectureIds;
    }

    public Long getPlagiarismCaseId() {
        return plagiarismCaseId;
    }

    public void setPlagiarismCaseId(Long plagiarismCaseId) {
        this.plagiarismCaseId = plagiarismCaseId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public boolean getFilterToUnresolved() {
        return filterToUnresolved;
    }

    public void setFilterToUnresolved(boolean filterToUnresolved) {
        this.filterToUnresolved = filterToUnresolved;
    }

    public boolean getFilterToOwn() {
        return filterToOwn;
    }

    public void setFilterToOwn(boolean filterToOwn) {
        this.filterToOwn = filterToOwn;
    }

    public boolean getFilterToAnsweredOrReacted() {
        return filterToAnsweredOrReacted;
    }

    public void setFilterToAnsweredOrReacted(boolean filterToAnsweredOrReacted) {
        this.filterToAnsweredOrReacted = filterToAnsweredOrReacted;
    }

    public PostSortCriterion getPostSortCriterion() {
        return postSortCriterion;
    }

    public void setPostSortCriterion(PostSortCriterion postSortCriterion) {
        this.postSortCriterion = postSortCriterion;
    }

    public SortingOrder getSortingOrder() {
        return sortingOrder;
    }

    public void setSortingOrder(SortingOrder sortingOrder) {
        this.sortingOrder = sortingOrder;
    }
}
