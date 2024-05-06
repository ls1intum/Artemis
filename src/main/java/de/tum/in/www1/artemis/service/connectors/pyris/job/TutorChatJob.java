package de.tum.in.www1.artemis.service.connectors.pyris.job;

import java.io.Serial;
import java.io.Serializable;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;

/**
 * An implementation of a PyrisJob for tutor chat messages.
 * This job is used to reference the details of a tutor chat session when Pyris sends a status update.
 * As it is stored within Hazelcast, it must be serializable.
 */
public class TutorChatJob extends PyrisJob implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    protected final long courseId;

    protected final long exerciseId;

    protected final long sessionId;

    public TutorChatJob(long courseId, long exerciseId, long sessionId) {
        super();
        this.courseId = courseId;
        this.exerciseId = exerciseId;
        this.sessionId = sessionId;
    }

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }

    @Override
    public boolean canAccess(Exercise exercise) {
        return exercise.getId().equals(exerciseId);
    }

    public long getCourseId() {
        return courseId;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public long getSessionId() {
        return sessionId;
    }
}
