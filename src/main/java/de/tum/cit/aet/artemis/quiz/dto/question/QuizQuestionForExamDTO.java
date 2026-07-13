package de.tum.cit.aet.artemis.quiz.dto.question;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Common supertype of the two exam quiz-question projections, so a single {@code quizQuestions} list can carry either
 * the solution-hidden conduction shape ({@link QuizQuestionWithoutSolutionDTO}) or the full post-publish summary shape
 * ({@link QuizQuestionWithSolutionDTO}). The concrete element type is selected by a publish flag threaded down from the
 * summary factory ({@code StudentExamForSummaryDTO.of}); the conduction path always uses the without-solution variant.
 * <p>
 * There is intentionally no {@code @JsonTypeInfo}: within a single response every element is the same concrete type, so
 * serialization by runtime type adds no discriminator and the wire is unchanged. For the reverse direction — server
 * integration tests that deserialize the response into the DTO type (e.g. {@code StudentExamWithGradeDTO}) — the
 * abstract interface is bound to the solution-carrying variant via {@code @JsonDeserialize(as = ...)}: it is a strict
 * superset (the without-solution fields are simply absent/null on a masked payload), so a payload of either shape reads
 * back losslessly without a wire-level discriminator.
 */
@JsonDeserialize(as = QuizQuestionWithSolutionDTO.class)
public sealed interface QuizQuestionForExamDTO permits QuizQuestionWithSolutionDTO, QuizQuestionWithoutSolutionDTO {
}
