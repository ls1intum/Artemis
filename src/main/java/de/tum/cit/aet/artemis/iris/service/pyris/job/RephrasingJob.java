package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * A pyris job that rephrases a text.
 *
 * @param jobId    the job id
 * @param courseId the course in which the rephrasing is being done
 * @param userId   the user who started the job
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RephrasingJob(String jobId, long courseId, long userId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }

}
