package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lightweight conduction counts for the instructor exam checklist, excluding test runs.
 *
 * @param numberOfGeneratedStudentExams generated attempts, including attempts opened on the coverpage
 * @param numberOfExamsStarted          started attempts
 * @param numberOfExamsSubmitted        submitted attempts
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamConductionCountsDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long numberOfGeneratedStudentExams,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long numberOfExamsStarted, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long numberOfExamsSubmitted) {
}
