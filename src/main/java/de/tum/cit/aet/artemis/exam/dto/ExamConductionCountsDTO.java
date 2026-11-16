package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Lightweight conduction counts for the instructor exam checklist, excluding test runs.
 *
 * @param numberOfGeneratedStudentExams generated attempts, including attempts opened on the coverpage
 * @param numberOfExamsStarted          started attempts
 * @param numberOfExamsSubmitted        submitted attempts
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamConductionCountsDTO(long numberOfGeneratedStudentExams, long numberOfExamsStarted, long numberOfExamsSubmitted) {
}
