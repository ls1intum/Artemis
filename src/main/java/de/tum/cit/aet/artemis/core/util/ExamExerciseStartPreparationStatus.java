package de.tum.cit.aet.artemis.core.util;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

public record ExamExerciseStartPreparationStatus(int finished, int failed, int overall, int participationCount, ZonedDateTime startedAt) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
