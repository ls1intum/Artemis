package de.tum.in.www1.artemis.service.dto.exam.monitoring.actions;

import de.tum.in.www1.artemis.service.dto.exam.monitoring.ExamActionDTO;

public class SwitchedExerciseActionDTO extends ExamActionDTO {

    private long exerciseId;

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
    }
}
