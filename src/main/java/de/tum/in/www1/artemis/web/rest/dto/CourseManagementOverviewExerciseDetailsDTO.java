package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;
import java.util.Set;

public class CourseManagementOverviewExerciseDetailsDTO {
    private Long exerciseId;

    private String exerciseTitle;

    private String exerciseType;

    private ZonedDateTime releaseDate;

    private ZonedDateTime dueDate;

    private ZonedDateTime assessmentDueDate;

    private Set<String> categories;

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public String getExerciseTitle() {
        return exerciseTitle;
    }

    public void setExerciseTitle(String exerciseTitle) {
        this.exerciseTitle = exerciseTitle;
    }

    public Set<String> getCategories() {
        return categories;
    }

    public void setCategories(Set<String> categories) {
        this.categories = categories;
    }

    public String getExerciseType() {
        return exerciseType;
    }

    public void setExerciseType(String exerciseType) {
        this.exerciseType = exerciseType;
    }

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(ZonedDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public ZonedDateTime getAssessmentDueDate() {
        return assessmentDueDate;
    }

    public void setAssessmentDueDate(ZonedDateTime assessmentDueDate) {
        this.assessmentDueDate = assessmentDueDate;
    }
}
