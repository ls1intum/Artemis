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

    private CourseWideContext courseWideContext;

    private Long exerciseId;

    private Long lectureId;

    private Long plagiarismCaseId;

    private String searchText;

    private Boolean filterToUnresolved;

    private Boolean filterToOwn;

    private Boolean filterToAnsweredOrReacted;

    private PostSortCriterion postSortCriterion;

    private SortingOrder sortingOrder;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public CourseWideContext getCourseWideContext() {
        return courseWideContext;
    }

    public void setCourseWideContext(CourseWideContext courseWideContext) {
        this.courseWideContext = courseWideContext;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Long getLectureId() {
        return lectureId;
    }

    public void setLectureId(Long lectureId) {
        this.lectureId = lectureId;
    }

    public Long getPlagiarismCaseId() {
        return plagiarismCaseId;
    }

    public void setPlagiarismCaseId(Long plagiarismCaseId) {
        this.plagiarismCaseId = plagiarismCaseId;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public Boolean getFilterToUnresolved() {
        return filterToUnresolved;
    }

    public void setFilterToUnresolved(Boolean filterToUnresolved) {
        this.filterToUnresolved = filterToUnresolved;
    }

    public Boolean getFilterToOwn() {
        return filterToOwn;
    }

    public void setFilterToOwn(Boolean filterToOwn) {
        this.filterToOwn = filterToOwn;
    }

    public Boolean getFilterToAnsweredOrReacted() {
        return filterToAnsweredOrReacted;
    }

    public void setFilterToAnsweredOrReacted(Boolean filterToAnsweredOrReacted) {
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
