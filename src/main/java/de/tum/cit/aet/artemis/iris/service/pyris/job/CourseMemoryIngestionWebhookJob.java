package de.tum.cit.aet.artemis.iris.service.pyris.job;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * An implementation of a PyrisJob for Course Memory ingestion in Pyris.
 * Used to reference the details of an ingestion run when Pyris sends a status update.
 *
 * @param jobId          the unique token identifying this job (also the Bearer token on the callback)
 * @param courseId       the id of the course the memory entry is scoped to
 * @param conversationId the stringified id of the originating thread
 * @param messageId      the stringified stable id of the answer message (dedup/upsert key)
 */
public record CourseMemoryIngestionWebhookJob(String jobId, long courseId, String conversationId, String messageId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return false;
    }

    @Override
    public boolean canAccess(Exercise exercise) {
        return false;
    }
}
