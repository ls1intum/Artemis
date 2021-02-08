package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

public class CourseManagementOverviewDTO {

    private Long courseId;

    private List<CourseManagementOverviewExerciseDetailsDTO> exerciseDetails;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public List<CourseManagementOverviewExerciseDetailsDTO> getExerciseDetails() {
        return exerciseDetails;
    }

    public void setExerciseDetails(List<CourseManagementOverviewExerciseDetailsDTO> exerciseDetails) {
        this.exerciseDetails = exerciseDetails;
    }
}
