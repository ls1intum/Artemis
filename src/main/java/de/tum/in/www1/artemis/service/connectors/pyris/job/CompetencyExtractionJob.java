package de.tum.in.www1.artemis.service.connectors.pyris.job;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;

/**
 *
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyExtractionJob(String jobId, long courseId, long userId) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }

}
