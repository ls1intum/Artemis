package de.tum.in.www1.artemis.service.connectors.pyris.job;

import java.io.Serializable;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

/**
 * This interface represents a single job that is executed by the Pyris system.
 * This is used to reference the details of a job when Pyris sends a status update.
 * As it is stored within Hazelcast, it must be serializable.
 */
public interface PyrisJob extends Serializable {

    long serialVersionUID = 1L;

    boolean canAccess(Course course);

    boolean canAccess(Exercise exercise);

    default boolean canAccess(LectureUnit lectureUnit) {
        return this.canAccess(lectureUnit.getLecture().getCourse());
    }

    String jobId();
}
