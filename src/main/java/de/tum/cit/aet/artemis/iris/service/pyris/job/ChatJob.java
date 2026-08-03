package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * @param clientId identifies the browser tab this run was started from, so a command Iris issues mid-pipeline can be addressed back to that same tab. Null for runs with no
 *                     originating client, e.g. event-triggered pipelines; a command is then broadcast to all of the user's tabs.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChatJob(String jobId, long courseId, long sessionId, Long entityId, Long traceId, Long userMessageId, Long assistantMessageId, String clientId)
        implements TrackedSessionBasedPyrisJob {

    public ChatJob(String jobId, long courseId, long sessionId, Long entityId, Long traceId, Long userMessageId, Long assistantMessageId) {
        this(jobId, courseId, sessionId, entityId, traceId, userMessageId, assistantMessageId, null);
    }

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }

    @Override
    public boolean canAccess(Exercise exercise) {
        return exercise.getId().equals(entityId);
    }

    @Override
    public ChatJob withUserMessageId(long messageId) {
        return new ChatJob(jobId, courseId, sessionId, entityId, traceId, messageId, assistantMessageId, clientId);
    }

    @Override
    public ChatJob withAssistantMessageId(long messageId) {
        return new ChatJob(jobId, courseId, sessionId, entityId, traceId, userMessageId, messageId, clientId);
    }

    @Override
    public ChatJob withTraceId(long traceId) {
        return new ChatJob(jobId, courseId, sessionId, entityId, traceId, userMessageId, assistantMessageId, clientId);
    }
}
