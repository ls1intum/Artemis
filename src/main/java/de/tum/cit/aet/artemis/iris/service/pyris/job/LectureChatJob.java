package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * An implementation of a PyrisJob for lecture chat messages.
 * This job is used to reference the details of a lecture chat session when Pyris sends a status update.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureChatJob(String jobId, long courseId, long lectureId, long sessionId, Long traceId, Long userMessageId, Long assistantMessageId)
        implements TrackedSessionBasedPyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }

    @Override
    public LectureChatJob withUserMessageId(long messageId) {
        return new LectureChatJob(jobId, courseId, lectureId, sessionId, traceId, messageId, assistantMessageId);
    }

    @Override
    public LectureChatJob withAssistantMessageId(long messageId) {
        return new LectureChatJob(jobId, courseId, lectureId, sessionId, traceId, userMessageId, messageId);
    }

    @Override
    public LectureChatJob withTraceId(long traceId) {
        return new LectureChatJob(jobId, courseId, lectureId, sessionId, traceId, userMessageId, assistantMessageId);
    }
}
