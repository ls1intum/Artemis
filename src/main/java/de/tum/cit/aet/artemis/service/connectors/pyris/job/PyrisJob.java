package de.tum.cit.aet.artemis.service.connectors.pyris.job;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * This interface represents a single job that is executed by the Pyris system.
 * This is used to reference the details of a job when Pyris sends a status update.
 * As it is stored within Hazelcast, it must be serializable.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface PyrisJob extends Serializable {

    long serialVersionUID = 1L;

    boolean canAccess(Course course);

    default boolean canAccess(Exercise exercise) {
        return this.canAccess(exercise.getCourseViaExerciseGroupOrCourseMember());
    }

    default boolean canAccess(LectureUnit lectureUnit) {
        return this.canAccess(lectureUnit.getLecture().getCourse());
    }

    String jobId();
}
