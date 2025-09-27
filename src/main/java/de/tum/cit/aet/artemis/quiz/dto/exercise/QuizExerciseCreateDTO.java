package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.dto.question.create.QuizQuestionCreateDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseCreateDTO(@NotEmpty String title, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, DifficultyLevel difficulty,
        @NotNull ExerciseMode mode, @NotNull IncludedInOverallScore includedInOverallScore, Set<CompetencyExerciseLink> competencyLinks, Set<String> categories,
        @NotNull String channelName, boolean randomizeQuestionOrder, @NotNull QuizMode quizMode, int duration, @NotEmpty List<? extends QuizQuestionCreateDTO> quizQuestions) {

    /**
     * Converts this DTO to a {@link QuizExercise} domain object.
     * <p>
     * Maps the DTO properties to the corresponding fields in the domain object, handling null-safe
     * collections for competency links and categories. Transforms the list of {@link QuizQuestionCreateDTO}
     * into a list of {@link de.tum.cit.aet.artemis.quiz.domain.QuizQuestion} objects by invoking their
     * respective {@code toDomainObject} methods.
     *
     * @return the {@link QuizExercise} domain object with properties and quiz questions set from this DTO
     */
    public QuizExercise toDomainObject() {
        QuizExercise quizExercise = new QuizExercise();
        quizExercise.setTitle(title);
        quizExercise.setReleaseDate(releaseDate);
        quizExercise.setStartDate(startDate);
        quizExercise.setDueDate(dueDate);
        quizExercise.setDifficulty(difficulty);
        quizExercise.setMode(mode);
        quizExercise.setIncludedInOverallScore(includedInOverallScore);
        quizExercise.setCompetencyLinks(competencyLinks == null ? Set.of() : competencyLinks);
        quizExercise.setCategories(categories == null ? Set.of() : categories);
        quizExercise.setChannelName(channelName);
        quizExercise.setRandomizeQuestionOrder(randomizeQuestionOrder);
        quizExercise.setQuizMode(quizMode);
        quizExercise.setDuration(duration);
        quizExercise.setQuizQuestions(quizQuestions.stream().map(QuizQuestionCreateDTO::toDomainObject).toList());
        return quizExercise;
    }

    /**
     * Creates a {@link QuizExerciseCreateDTO} from the given {@link QuizExercise} domain object.
     * <p>
     * Maps the domain object's properties to the corresponding DTO fields, handling null-safe
     * collections for competency links and categories. Transforms the list of {@link de.tum.cit.aet.artemis.quiz.domain.QuizQuestion}
     * into a list of {@link QuizQuestionCreateDTO} objects by invoking their respective {@code of} methods.
     *
     * @param quizExercise the {@link QuizExercise} domain object to convert
     * @return the {@link QuizExerciseCreateDTO} with properties and quiz question DTOs set from the domain object
     */
    public static QuizExerciseCreateDTO of(QuizExercise quizExercise) {
        List<QuizQuestionCreateDTO> questionDTOs = quizExercise.getQuizQuestions().stream().map(QuizQuestionCreateDTO::of).toList();
        return new QuizExerciseCreateDTO(quizExercise.getTitle(), quizExercise.getReleaseDate(), quizExercise.getStartDate(), quizExercise.getDueDate(),
                quizExercise.getDifficulty(), quizExercise.getMode(), quizExercise.getIncludedInOverallScore(), quizExercise.getCompetencyLinks(), quizExercise.getCategories(),
                quizExercise.getChannelName(), quizExercise.isRandomizeQuestionOrder(), quizExercise.getQuizMode(), quizExercise.getDuration(), questionDTOs);
    }
}
