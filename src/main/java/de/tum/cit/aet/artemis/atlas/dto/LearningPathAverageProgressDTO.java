
package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for returning average learning path progress information for a course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathAverageProgressDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long courseId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double averageProgress, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalStudents) {
}
