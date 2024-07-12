package de.tum.in.www1.artemis.service.connectors.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;

/**
 * An implementation of a PyrisJob for exercise chat messages.
 * This job is used to reference the details of a exercise chat session when Pyris sends a status update.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseChatJob(String jobId, long courseId, long exerciseId, long sessionId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }

    @Override
    public boolean canAccess(Exercise exercise) {
        return exercise.getId().equals(exerciseId);
    }
}
