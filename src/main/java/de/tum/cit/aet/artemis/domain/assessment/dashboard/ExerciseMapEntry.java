package de.tum.cit.aet.artemis.domain.assessment.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseMapEntry(long exerciseId, long value) {
}
