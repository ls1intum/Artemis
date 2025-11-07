package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * An implementation of a PyrisJob for exercise chat messages.
 * This job is used to reference the details of a exercise chat session when Pyris sends a status update.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorSuggestionJob(String jobId, long postId, long courseId, long sessionId, Long traceId, Long userMessageId, Long assistantMessageId)
        implements TrackedSessionBasedPyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }

    @Override
    public Long userMessageId() {
        return userMessageId;
    }

    @Override
    public TrackedSessionBasedPyrisJob withUserMessageId(long messageId) {
        return new TutorSuggestionJob(jobId, postId, courseId, sessionId, traceId, messageId, assistantMessageId);
    }

    @Override
    public TrackedSessionBasedPyrisJob withAssistantMessageId(long messageId) {
        return new TutorSuggestionJob(jobId, postId, courseId, sessionId, traceId, userMessageId, messageId);
    }

    @Override
    public TrackedSessionBasedPyrisJob withTraceId(long traceId) {
        return new TutorSuggestionJob(jobId, postId, courseId, sessionId, traceId, userMessageId, assistantMessageId);
    }
}
