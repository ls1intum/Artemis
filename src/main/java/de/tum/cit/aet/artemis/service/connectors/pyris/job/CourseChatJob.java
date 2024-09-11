package de.tum.cit.aet.artemis.service.connectors.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Course;

/**
 * An implementation of a PyrisJob for course chat messages.
 * This job is used to reference the details of a course chat session when Pyris sends a status update.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseChatJob(String jobId, long courseId, long sessionId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return courseId == course.getId();
    }
}
