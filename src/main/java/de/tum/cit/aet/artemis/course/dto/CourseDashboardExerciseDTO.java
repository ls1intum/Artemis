package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseOverviewDTO;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/** Student-visible exercise graph carried by the deprecated dashboard contract. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseDashboardExerciseDTO(@JsonUnwrapped ExerciseOverviewDTO overview, @Nullable String problemStatement, @Nullable String gradingInstructions,
        @Nullable String exampleSolution, @Nullable String exampleSolutionModel, @Nullable String exampleSolutionExplanation,
        @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable List<QuizQuestionWithSolutionDTO> quizQuestions) {

    /** Maps an exercise after student-sensitive fields and results have been filtered. */
    public static CourseDashboardExerciseDTO of(Exercise exercise) {
        String exampleSolution = switch (exercise) {
            case TextExercise textExercise -> textExercise.getExampleSolution();
            case FileUploadExercise fileUploadExercise -> fileUploadExercise.getExampleSolution();
            default -> null;
        };
        ModelingExercise modelingExercise = exercise instanceof ModelingExercise modeling ? modeling : null;
        List<QuizQuestionWithSolutionDTO> quizQuestions = null;
        if (exercise instanceof QuizExercise quizExercise && Hibernate.isInitialized(quizExercise.getQuizQuestions())) {
            quizQuestions = quizExercise.getQuizQuestions().stream().map(QuizQuestionWithSolutionDTO::of).toList();
        }
        return new CourseDashboardExerciseDTO(ExerciseOverviewDTO.of(exercise), exercise.getProblemStatement(), exercise.getGradingInstructions(), exampleSolution,
                modelingExercise == null ? null : modelingExercise.getExampleSolutionModel(), modelingExercise == null ? null : modelingExercise.getExampleSolutionExplanation(),
                exercise.getExampleSolutionPublicationDate(), quizQuestions);
    }
}
