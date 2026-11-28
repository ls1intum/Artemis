package de.tum.cit.aet.artemis.assessment.dto.score;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Independent participants count once for sample sufficiency; team results are student-weighted. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PeerExerciseAverageDTO(long exerciseId, double average, long participantCount) {
}
