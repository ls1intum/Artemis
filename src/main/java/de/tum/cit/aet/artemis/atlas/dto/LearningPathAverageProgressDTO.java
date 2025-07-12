
package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for returning average learning path progress information for a course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathAverageProgressDTO(long courseId, double averageProgress, long totalStudents) {
}
