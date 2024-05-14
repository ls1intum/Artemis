package de.tum.in.www1.artemis.service.connectors.pyris.job;

import de.tum.in.www1.artemis.domain.Course;

/**
 * An implementation of a PyrisJob for course chat messages.
 * This job is used to reference the details of a course chat session when Pyris sends a status update.
 */
public record CourseChatJob(String jobId, long courseId, long sessionId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }
}
