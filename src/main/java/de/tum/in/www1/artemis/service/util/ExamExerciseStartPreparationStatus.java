package de.tum.in.www1.artemis.service.util;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

public record ExamExerciseStartPreparationStatus(int finished, int failed, int overall, int participationCount, ZonedDateTime startedAt) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
