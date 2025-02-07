package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureChatJob(String jobId, long courseId, long lectureId, long sessionId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }
}
