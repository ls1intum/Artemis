package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;
import java.util.Set;

import de.tum.in.www1.artemis.domain.Exercise;

public class CourseManagementOverviewCourseDTO {
    private Long courseId;

    private List<CourseManagementOverviewExerciseDetailsDTO> exerciseDTOS;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public List<CourseManagementOverviewExerciseDetailsDTO> getExerciseDTOS() {
        return exerciseDTOS;
    }

    public void setExerciseDTOS(List<CourseManagementOverviewExerciseDetailsDTO> exerciseDTOS) {
        this.exerciseDTOS = exerciseDTOS;
    }
}
