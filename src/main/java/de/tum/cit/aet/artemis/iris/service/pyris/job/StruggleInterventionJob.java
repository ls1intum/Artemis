package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Flat one-shot Hazelcast job for a proactive struggle-intervention run. The session is NOT stored (it is
 * created only on an {@code active} outcome, §11); the callback resolves it from {@code exerciseId} +
 * {@code userId}. {@code jobId == settings.authenticationToken == Bearer run_id}.
 *
 * @param jobId      the job id (== authentication token == Bearer run_id)
 * @param courseId   the course the run belongs to; authorizes {@link #canAccess(Course)}
 * @param exerciseId the exercise the student is struggling on
 * @param userId     the struggling student
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StruggleInterventionJob(String jobId, long courseId, long exerciseId, long userId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }
}
