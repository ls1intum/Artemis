package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * A Pyris job to check an exercise for consistency.
 *
 * @param jobId      the job id
 * @param courseId   the course in which the consistency check is being done (permission checking)
 * @param exerciseId the exercise in which the consistency check is being done
 * @param userId     the user who started the job
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConsistencyCheckJob(String jobId, long courseId, long exerciseId, long userId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return false;
    }
}
