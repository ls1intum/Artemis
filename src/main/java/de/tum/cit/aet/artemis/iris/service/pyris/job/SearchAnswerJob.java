package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * A PyrisJob for global search answer requests.
 * Stores the user login so that WebSocket messages can be routed to the correct user.
 * Global search is not course-scoped, so canAccess always returns false.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SearchAnswerJob(String jobId, String userLogin) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return false;
    }
}
