package de.tum.in.www1.artemis.web.rest.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.exam.Exam;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CoursesForDashboardDTO(Set<CourseForDashboardDTO> courses, Set<Exam> activeExams) {

}
