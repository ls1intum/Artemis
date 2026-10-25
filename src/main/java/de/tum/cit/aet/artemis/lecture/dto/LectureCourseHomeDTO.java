package de.tum.cit.aet.artemis.lecture.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureCourseHomeDTO(CourseHomeDTO.SectionStatus status, List<CourseHomeDTO.Item> items) {
}
