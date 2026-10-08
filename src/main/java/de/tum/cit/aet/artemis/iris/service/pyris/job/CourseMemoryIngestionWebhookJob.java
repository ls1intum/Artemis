package de.tum.cit.aet.artemis.iris.service.pyris.job;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.CourseMemoryOperation;

/**
 * An implementation of a PyrisJob for Course Memory ingestion and deletion in Pyris.
 * Used to reference the details of a run when Pyris sends a status update. Deletion reuses this job
 * type because Pyris drives both through the same status callback, so {@code operation} is what
 * tells the two apart when the run terminates.
 *
 * @param jobId          the unique token identifying this job (also the Bearer token on the callback)
 * @param courseId       the id of the course the memory entry is scoped to
 * @param conversationId the stringified id of the channel the thread lives in
 * @param postId         the stringified id of the thread's root post (dedup/upsert key)
 * @param messageId      the stringified id of the answer message that triggered the run, or {@code null}
 *                           for a deletion, which targets the thread rather than a single answer
 * @param userLogin      login of the user who triggered the run, notified when it terminates, or
 *                           {@code null} when the actor is unknown — the run still proceeds, it just
 *                           reports to nobody
 * @param operation      whether this run writes or removes the entry
 */
public record CourseMemoryIngestionWebhookJob(String jobId, long courseId, String conversationId, String postId, @Nullable String messageId, @Nullable String userLogin,
        CourseMemoryOperation operation) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return false;
    }

    @Override
    public boolean canAccess(Exercise exercise) {
        return false;
    }
}
