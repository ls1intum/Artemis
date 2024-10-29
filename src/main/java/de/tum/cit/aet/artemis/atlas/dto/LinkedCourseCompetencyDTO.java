package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;

/**
 * A DTO containing the information of the linkedCourseCompetency field of a
 * {@link CourseCompetency CourseCompetency}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record LinkedCourseCompetencyDTO(long id, long courseId, String courseTitle, String semester) {

}
