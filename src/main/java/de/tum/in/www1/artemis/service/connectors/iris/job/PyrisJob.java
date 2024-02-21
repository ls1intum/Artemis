package de.tum.in.www1.artemis.service.connectors.iris.job;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.service.connectors.iris.dto.PyrisResultDTO;
import de.tum.in.www1.artemis.service.connectors.iris.dto.PyrisStatusUpdateDTO;

public abstract class PyrisJob {

    public abstract void handleStatusUpdate(PyrisStatusUpdateDTO statusUpdate);

    public abstract void handleResult(PyrisResultDTO result);

    public abstract boolean canAccess(Course course);

    public abstract boolean canAccess(Exercise exercise);

    public boolean canAccess(LectureUnit lectureUnit) {
        return this.canAccess(lectureUnit.getLecture().getCourse());
    }
}
