package de.tum.cit.aet.artemis.assessment.dto.score;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Bulk sufficient statistics for a student's mean assessed exercise percentage. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentScoreAverageDTO(long studentId, double scoreSum, long scoreCount) {
}
