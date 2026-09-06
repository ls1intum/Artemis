package de.tum.cit.aet.artemis.quiz.dto.question;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Common supertype of the two exam quiz-question projections, so a single {@code quizQuestions} list can carry either
 * the solution-hidden conduction shape ({@link QuizQuestionWithoutSolutionDTO}) or the full post-publish summary shape
 * ({@link QuizQuestionWithSolutionDTO}). The concrete element type is selected by a publish flag threaded down from the
 * summary factory ({@code StudentExamForSummaryDTO.of}); the conduction path always uses the without-solution variant.
 * <p>
 * There is intentionally no {@code @JsonTypeInfo} on this interface. Both subinterfaces carry their own, keyed on the
 * {@code type} property that tells the question types apart, but nothing on the wire distinguishes the solution-hidden
 * shape from the full one — a masked payload simply omits the solution fields. So the with/without choice cannot be
 * made from the payload, and binding it here would leak onto every concrete record through Jackson's annotation
 * inheritance. Instead the one site that reads these back ({@code QuizExerciseForConductionDTO#quizQuestions}) names
 * the variant it wants with {@code @JsonDeserialize(contentAs = ...)}, which is where the ambiguity actually lives.
 */
@Schema(oneOf = { QuizQuestionWithSolutionDTO.class,
        QuizQuestionWithoutSolutionDTO.class }, description = "Exam quiz question projection: solution-hidden during conduction and before results are published, full solutions afterwards (and on test runs)")
public sealed interface QuizQuestionForExamDTO permits QuizQuestionWithSolutionDTO, QuizQuestionWithoutSolutionDTO {
}
