package de.tum.cit.aet.artemis.exam.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.CourseHomeSectionStatus;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamCourseHomeDTO(CourseHomeSectionStatus status, List<CourseHomeDTO.ItemDTO> items) {
}
