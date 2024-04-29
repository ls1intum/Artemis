package de.tum.in.www1.artemis.service.connectors.pyris.job;

import java.io.Serial;
import java.io.Serializable;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;

public class CourseChatJob extends PyrisJob implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    protected final long courseId;

    protected final long sessionId;

    public CourseChatJob(long courseId, long sessionId) {
        super();
        this.courseId = courseId;
        this.sessionId = sessionId;
    }

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }

    @Override
    public boolean canAccess(Exercise exercise) {
        return false;
    }

    public long getCourseId() {
        return courseId;
    }

    public long getSessionId() {
        return sessionId;
    }
}
