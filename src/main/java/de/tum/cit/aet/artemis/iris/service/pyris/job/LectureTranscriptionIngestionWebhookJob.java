package de.tum.cit.aet.artemis.iris.service.pyris.job;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

public record LectureTranscriptionIngestionWebhookJob(String jobId, long courseId, long lectureId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return false;
    }

    @Override
    public boolean canAccess(Exercise exercise) {
        return false;
    }
}
