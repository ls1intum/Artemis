package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChatJob(String jobId, long courseId, long sessionId, Long entityId, Long traceId, Long userMessageId, Long assistantMessageId)
        implements TrackedSessionBasedPyrisJob {

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
        return new ChatJob(jobId, courseId, sessionId, entityId, traceId, messageId, assistantMessageId);
    }

    @Override
    public ChatJob withAssistantMessageId(long messageId) {
        return new ChatJob(jobId, courseId, sessionId, entityId, traceId, userMessageId, messageId);
    }

    @Override
    public ChatJob withTraceId(long traceId) {
        return new ChatJob(jobId, courseId, sessionId, entityId, traceId, userMessageId, assistantMessageId);
    }
}
