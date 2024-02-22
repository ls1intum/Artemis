package de.tum.in.www1.artemis.service.connectors.pyris.job;

import java.io.Serializable;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisResultDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisStatusUpdateDTO;

public abstract class PyrisJob implements Serializable {

    public abstract void handleStatusUpdate(PyrisStatusUpdateDTO statusUpdate);

    public abstract void handleResult(PyrisResultDTO result);

    public abstract boolean canAccess(Course course);

    public abstract boolean canAccess(Exercise exercise);

    public boolean canAccess(LectureUnit lectureUnit) {
        return this.canAccess(lectureUnit.getLecture().getCourse());
    }
}
