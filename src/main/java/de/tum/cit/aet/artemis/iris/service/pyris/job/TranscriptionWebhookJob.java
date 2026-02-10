package de.tum.cit.aet.artemis.iris.service.pyris.job;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * An implementation of PyrisJob for Video Transcription webhooks.
 * This job is used to reference the details of a video transcription when Pyris sends a status update callback.
 */
public record TranscriptionWebhookJob(String jobId, long courseId, long lectureId, long lectureUnitId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return false;
    }

    @Override
    public boolean canAccess(Exercise exercise) {
        return false;
    }
}
