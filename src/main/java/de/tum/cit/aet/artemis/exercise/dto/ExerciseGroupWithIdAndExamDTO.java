package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.artemis.exam.dto.ExamWithIdAndCourseDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGroupWithIdAndExamDTO(long id, ExamWithIdAndCourseDTO exam) {
}
