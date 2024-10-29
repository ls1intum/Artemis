package de.tum.cit.aet.artemis.assessment.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseMapEntryDTO(long exerciseId, long value) {

    public ExerciseMapEntryDTO(Long exerciseId, Long value) {
        this(exerciseId != null ? exerciseId : 0, value != null ? value : 0);
    }
}
