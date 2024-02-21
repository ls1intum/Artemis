package de.tum.in.www1.artemis.service.connectors.iris.job;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.service.connectors.iris.dto.PyrisResultDTO;
import de.tum.in.www1.artemis.service.connectors.iris.dto.PyrisStatusUpdateDTO;

public class RepositoryWebhookJob extends PyrisJob {

    protected final long exerciseId;

    public RepositoryWebhookJob(long exerciseId) {
        super();
        this.exerciseId = exerciseId;
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
        return false;
    }

    @Override
    public boolean canAccess(Exercise exercise) {
        return exercise.getId().equals(exerciseId);
    }
}
