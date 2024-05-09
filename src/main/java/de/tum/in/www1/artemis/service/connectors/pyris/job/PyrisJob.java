package de.tum.in.www1.artemis.service.connectors.pyris.job;

import java.io.Serializable;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

/**
 * This abstract class represents a single job that is executed by the Pyris system.
 * This is used to reference the details of a job when Pyris sends a status update.
 * As it is stored within Hazelcast, it must be serializable.
 */
public abstract class PyrisJob implements Serializable {

    protected String id;

    public abstract boolean canAccess(Course course);

    public abstract boolean canAccess(Exercise exercise);

    public boolean canAccess(LectureUnit lectureUnit) {
        return this.canAccess(lectureUnit.getLecture().getCourse());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
