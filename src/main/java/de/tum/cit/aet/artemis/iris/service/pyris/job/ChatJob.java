package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * An implementation of a PyrisJob for exercise, lecture, or course chat messages.
 * This job is used to reference the details of a chat session when Pyris sends a status update, and tracks which
 * pipeline (regular chat or ask-user-mode) the job belongs to.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChatJob(String jobId, long courseId, long sessionId, Long entityId, Long traceId, Long userMessageId, Long assistantMessageId, String pipelineName)
        implements TrackedSessionBasedPyrisJob {

    public static final String CHAT_PIPELINE_NAME = "chat";

    public static final String ASK_USER_PIPELINE_NAME = "ask-user";

    /**
     * Canonical constructor that defaults {@code pipelineName} to {@link #CHAT_PIPELINE_NAME} when {@code null} is
     * passed, so existing callers that do not specify a pipeline keep behaving as regular chat jobs.
     */
    public ChatJob {
        if (pipelineName == null) {
            pipelineName = CHAT_PIPELINE_NAME;
        }
    }

    /**
     * Convenience constructor for a regular chat job, defaulting the pipeline name to {@link #CHAT_PIPELINE_NAME}.
     */
    public ChatJob(String jobId, long courseId, long sessionId, Long entityId, Long traceId, Long userMessageId, Long assistantMessageId) {
        this(jobId, courseId, sessionId, entityId, traceId, userMessageId, assistantMessageId, CHAT_PIPELINE_NAME);
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
        return new ChatJob(jobId, courseId, sessionId, entityId, traceId, messageId, assistantMessageId, pipelineName);
    }

    @Override
    public ChatJob withAssistantMessageId(long messageId) {
        return new ChatJob(jobId, courseId, sessionId, entityId, traceId, userMessageId, messageId, pipelineName);
    }

    @Override
    public ChatJob withTraceId(long traceId) {
        return new ChatJob(jobId, courseId, sessionId, entityId, traceId, userMessageId, assistantMessageId, pipelineName);
    }

    public boolean isAskUserPipeline() {
        return ASK_USER_PIPELINE_NAME.equals(pipelineName);
    }
}
