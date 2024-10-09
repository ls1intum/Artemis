package de.tum.cit.aet.artemis.iris.service.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextExerciseChatJob(String jobId, long courseId, long exerciseId, long sessionId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }

    @Override
    public boolean canAccess(Exercise exercise) {
        return exercise.getId().equals(exerciseId);
    }
}
