package de.tum.in.www1.artemis.service.util;

import java.time.ZonedDateTime;

public record ExamExerciseStartPreparationStatus(long examId, int finished, int failed, int overall, ZonedDateTime startedAt) {
}
