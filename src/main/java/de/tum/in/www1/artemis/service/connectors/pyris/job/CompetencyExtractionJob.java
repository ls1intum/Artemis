package de.tum.in.www1.artemis.service.connectors.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;

/**
 * A pyris job that extracts competencies from a course description.
 *
 * @param jobId     the job id
 * @param courseId  the course in which the competencies are being extracted
 * @param userLogin the user login of the user who started the job
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyExtractionJob(String jobId, long courseId, String userLogin) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }

}
