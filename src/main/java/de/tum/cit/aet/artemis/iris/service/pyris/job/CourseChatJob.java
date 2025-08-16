package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * An implementation of a PyrisJob for course chat messages.
 * This job is used to reference the details of a course chat session when Pyris sends a status update.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseChatJob(String jobId, long courseId, long sessionId, Long traceId, Long userMessageId, Long assistantMessageId) implements TrackedSessionBasedPyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return courseId == course.getId();
    }

    @Override
    public TrackedSessionBasedPyrisJob withUserMessageId(long messageId) {
        return new CourseChatJob(jobId, courseId, sessionId, traceId, messageId, assistantMessageId);
    }

    @Override
    public TrackedSessionBasedPyrisJob withAssistantMessageId(long messageId) {
        return new CourseChatJob(jobId, courseId, sessionId, traceId, userMessageId, messageId);
    }

    @Override
    public TrackedSessionBasedPyrisJob withTraceId(long traceId) {
        return new CourseChatJob(jobId, courseId, sessionId, traceId, userMessageId, assistantMessageId);
    }
}
