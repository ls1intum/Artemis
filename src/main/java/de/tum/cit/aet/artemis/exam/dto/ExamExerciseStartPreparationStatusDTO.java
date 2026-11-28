package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.util.ExamExerciseStartPreparationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Progress of preparing the exercises of all student exams of an exam.
 *
 * @param finished           the number of student exams whose exercises are prepared
 * @param failed             the number of student exams whose preparation failed
 * @param overall            the number of student exams to prepare
 * @param participationCount the number of participations created so far
 * @param startedAt          when the preparation started
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamExerciseStartPreparationStatusDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int finished,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int failed, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int overall, int participationCount,
        ZonedDateTime startedAt) {

    /**
     * Maps the cached status to its response.
     *
     * @param status the cached status
     * @return the response
     */
    public static ExamExerciseStartPreparationStatusDTO of(ExamExerciseStartPreparationStatus status) {
        return new ExamExerciseStartPreparationStatusDTO(status.finished(), status.failed(), status.overall(), status.participationCount(), status.startedAt());
    }
}
