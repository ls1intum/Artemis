package de.tum.cit.aet.artemis.plagiarism.api.dtos;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

/**
 * The plagiarism information needed by the course score calculation.
 *
 * @param studentId      the affected student
 * @param exerciseId     the affected exercise
 * @param verdict        the plagiarism verdict, if one has been made
 * @param pointDeduction the percentage deducted from the exercise score for a point-deduction verdict
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseScoreDTO(long studentId, long exerciseId, @Nullable PlagiarismVerdict verdict, int pointDeduction) {
}
