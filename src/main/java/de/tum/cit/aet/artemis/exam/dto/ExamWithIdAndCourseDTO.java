package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.artemis.core.dto.CourseWithIdDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamWithIdAndCourseDTO(long id, CourseWithIdDTO course) {
}
