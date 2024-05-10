package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseManagementOverviewStatisticsDTO(Long courseId, List<Integer> activeStudents, List<CourseManagementOverviewExerciseStatisticsDTO> exerciseDTOS) {

}
