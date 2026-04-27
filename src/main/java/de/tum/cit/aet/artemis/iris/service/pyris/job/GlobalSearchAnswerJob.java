package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * A PyrisJob representing an in-flight lecture search / ask-Iris request.
 * Stores the requesting user's login so status updates can be forwarded via WebSocket.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GlobalSearchAnswerJob(String jobId, String userLogin) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        // Lecture search is not scoped to a specific course.
        return false;
    }
}
