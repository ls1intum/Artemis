package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * A Pyris job for the autonomous tutor pipeline that responds to student posts.
 * Unlike session-based jobs, this is a one-shot operation without ongoing conversation.
 *
 * @param jobId    the job id
 * @param postId   the post being responded to
 * @param courseId the course the post belongs to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AutonomousTutorJob(String jobId, long postId, long courseId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }
}
