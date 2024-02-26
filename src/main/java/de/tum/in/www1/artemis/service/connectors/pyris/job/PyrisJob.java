package de.tum.in.www1.artemis.service.connectors.pyris.job;

import java.io.Serializable;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

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
