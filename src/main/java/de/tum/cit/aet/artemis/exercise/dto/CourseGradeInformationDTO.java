package de.tum.cit.aet.artemis.exercise.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseGradeInformationDTO(List<GradeScoreDTO> gradeScores, List<StudentDTO> students) {
}
