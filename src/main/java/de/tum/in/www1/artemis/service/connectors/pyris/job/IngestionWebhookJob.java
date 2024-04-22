package de.tum.in.www1.artemis.service.connectors.pyris.job;

import java.io.Serializable;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;

public class IngestionWebhookJob extends PyrisJob implements Serializable {

    @Override
    public boolean canAccess(Course course) {
        return false;
    }

    @Override
    public boolean canAccess(Exercise exercise) {
        return false;
    }

    public IngestionWebhookJob() {
        super();
    }
}
