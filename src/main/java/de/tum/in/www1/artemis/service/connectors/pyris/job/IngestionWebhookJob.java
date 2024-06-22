package de.tum.in.www1.artemis.service.connectors.pyris.job;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;

/**
 * An implementation of a PyrisJob for Lecture Ingestion in Pyris.
 * This job is used to reference the details of then Ingestion when Pyris sends a status update.
 */
public record IngestionWebhookJob(String jobId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return false;
    }

    @Override
    public boolean canAccess(Exercise exercise) {
        return false;
    }
}
