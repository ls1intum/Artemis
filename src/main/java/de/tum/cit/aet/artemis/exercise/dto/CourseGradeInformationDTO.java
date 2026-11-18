package de.tum.cit.aet.artemis.exercise.dto;

import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseGradeInformationDTO(Collection<CourseGradeScoreDTO> gradeScores, Collection<StudentDTO> students) {
}
