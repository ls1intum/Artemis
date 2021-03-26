package de.tum.in.www1.artemis.domain.assessment.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseMapEntry {

    private final long exerciseId;

    private final long value;

    public long getExerciseId() {
        return exerciseId;
    }

    public long getValue() {
        return value;
    }

    public ExerciseMapEntry(long exerciseId, long value) {
        this.exerciseId = exerciseId;
        this.value = value;
    }

    public Long getKey() {
        return exerciseId;
    }

}
