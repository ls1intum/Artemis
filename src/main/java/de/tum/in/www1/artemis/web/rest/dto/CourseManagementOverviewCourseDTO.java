package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;
import java.util.Set;

import de.tum.in.www1.artemis.domain.Exercise;

public class CourseManagementOverviewCourseDTO {

    private Long courseId;

    private Integer[] activeStudents;

    private Set<Exercise> exercises;

    private List<CourseExerciseStatisticsDTO> exerciseDTOS;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Integer[] getActiveStudents() {
        return activeStudents;
    }

    public void setActiveStudents(Integer[] activeStudents) {
        this.activeStudents = activeStudents;
    }

    public Set<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(Set<Exercise> exercises) {
        this.exercises = exercises;
    }

    public List<CourseExerciseStatisticsDTO> getExerciseDTOS() {
        return exerciseDTOS;
    }

    public void setExerciseDTOS(List<CourseExerciseStatisticsDTO> exerciseDTOS) {
        this.exerciseDTOS = exerciseDTOS;
    }
}
