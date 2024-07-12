package de.tum.in.www1.artemis.web.rest.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO containing the information of the linkedCourseCompetency field of a
 * {@link de.tum.in.www1.artemis.domain.competency.CourseCompetency CourseCompetency}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record LinkedCourseCompetencyDTO(long id, long courseId, String courseTitle, String semester) {

}
