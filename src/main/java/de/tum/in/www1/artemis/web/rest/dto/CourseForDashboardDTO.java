package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.Course;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseForDashboardDTO {

    private Course course;

    private Map<String, CourseScoresDTO> scoresPerExerciseType;

    public void setCourse(Course course) {
        this.course = course;
    }

    public void setScoresPerExerciseType(Map<String, CourseScoresDTO> scoresPerExerciseType) {
        this.scoresPerExerciseType = scoresPerExerciseType;
    }

}
