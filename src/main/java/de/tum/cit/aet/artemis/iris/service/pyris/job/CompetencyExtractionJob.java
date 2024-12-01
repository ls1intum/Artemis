package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * A pyris job that extracts competencies from a course description.
 *
 * @param jobId    the job id
 * @param courseId the course in which the competencies are being extracted
 * @param userId   the user who started the job
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyExtractionJob(String jobId, long courseId, long userId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }

}
