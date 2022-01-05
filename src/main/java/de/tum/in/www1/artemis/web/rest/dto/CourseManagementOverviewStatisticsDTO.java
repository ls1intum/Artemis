package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseManagementOverviewStatisticsDTO {

    private Long courseId;

    private List<Integer> activeStudents;

    private List<CourseManagementOverviewExerciseStatisticsDTO> exerciseDTOS;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public List<Integer> getActiveStudents() {
        return activeStudents;
    }

    public void setActiveStudents(List<Integer> activeStudents) {
        this.activeStudents = activeStudents;
    }

    public List<CourseManagementOverviewExerciseStatisticsDTO> getExerciseDTOS() {
        return exerciseDTOS;
    }

    public void setExerciseDTOS(List<CourseManagementOverviewExerciseStatisticsDTO> exerciseDTOS) {
        this.exerciseDTOS = exerciseDTOS;
    }
}
