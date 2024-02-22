package de.tum.in.www1.artemis.service.connectors.pyris.job;

import java.io.Serial;
import java.io.Serializable;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisResultDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisStatusUpdateDTO;

public class LectureWebhookJob extends PyrisJob implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    protected final long courseId;

    public LectureWebhookJob(long courseId) {
        super();
        this.courseId = courseId;
    }

    @Override
    public void handleStatusUpdate(PyrisStatusUpdateDTO statusUpdate) {
        // Not implemented
    }

    @Override
    public void handleResult(PyrisResultDTO result) {
        // Not implemented
    }

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }

    @Override
    public boolean canAccess(Exercise exercise) {
        return false;
    }
}
