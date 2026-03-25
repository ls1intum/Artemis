package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchCreationDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.create.QuizQuestionCreateDTO;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizExerciseCreateDTO(@NotEmpty String title, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, DifficultyLevel difficulty,
        @NotNull ExerciseMode mode, @NotNull IncludedInOverallScore includedInOverallScore, Set<CompetencyLinkDTO> competencyLinks, Set<String> categories, String channelName,
        Boolean randomizeQuestionOrder, @NotNull QuizMode quizMode, Integer duration, Set<QuizBatchCreationDTO> quizBatches,
        @NotEmpty List<@Valid ? extends QuizQuestionCreateDTO> quizQuestions) {

    /**
     * Creates a {@link QuizExerciseCreateDTO} from the given {@link QuizExercise} domain object.
     * <p>
     * Maps the domain object's properties to the corresponding DTO fields, handling null-safe
     * collections for competency links and categories. Transforms the list of {@link QuizQuestion}
     * into a list of {@link QuizQuestionCreateDTO} objects by invoking their respective {@code of} methods.
     *
     * @param quizExercise the {@link QuizExercise} domain object to convert
     * @return the {@link QuizExerciseCreateDTO} with properties and quiz question DTOs set from the domain object
     */
    public static QuizExerciseCreateDTO of(QuizExercise quizExercise) {
        List<QuizQuestionCreateDTO> questionDTOs = quizExercise.getQuizQuestions().stream().map(QuizQuestionCreateDTO::of).toList();
        Set<QuizBatchCreationDTO> quizBatchDTOs = Optional.ofNullable(quizExercise.getQuizBatches()).orElse(Set.of()).stream().map(QuizBatchCreationDTO::of)
                .collect(Collectors.toSet());
        // Only convert competency links if they are initialized (to avoid LazyInitializationException)
        Set<CompetencyLinkDTO> competencyLinkDTOs = null;
        if (Hibernate.isInitialized(quizExercise.getCompetencyLinks()) && quizExercise.getCompetencyLinks() != null) {
            competencyLinkDTOs = quizExercise.getCompetencyLinks().stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet());
        }
        return new QuizExerciseCreateDTO(quizExercise.getTitle(), quizExercise.getReleaseDate(), quizExercise.getStartDate(), quizExercise.getDueDate(),
                quizExercise.getDifficulty(), quizExercise.getMode(), quizExercise.getIncludedInOverallScore(), competencyLinkDTOs, quizExercise.getCategories(),
                quizExercise.getChannelName(), quizExercise.isRandomizeQuestionOrder(), quizExercise.getQuizMode(), quizExercise.getDuration(), quizBatchDTOs, questionDTOs);
    }

    /**
     * Converts this DTO to a {@link QuizExercise} domain object.
     * <p>
     * Maps the DTO properties to the corresponding fields in the domain object, handling null-safe
     * collections for competency links and categories. Transforms the list of {@link QuizQuestionCreateDTO}
     * into a list of {@link QuizQuestion} objects by invoking their
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
        // Competency links are handled separately via DTOs in the controller;
        // they are passed to createQuizExercise() directly and resolved there
        quizExercise.setCompetencyLinks(Set.of());
        quizExercise.setCategories(categories == null ? Set.of() : categories);
        quizExercise.setChannelName(channelName);
        quizExercise.setRandomizeQuestionOrder(randomizeQuestionOrder != null ? randomizeQuestionOrder : Boolean.FALSE);
        quizExercise.setQuizMode(quizMode);
        quizExercise.setDuration(duration);
        quizExercise.setQuizBatches(Optional.ofNullable(quizBatches).orElse(Set.of()).stream().map(QuizBatchCreationDTO::toDomainObject).collect(Collectors.toSet()));
        quizExercise.setQuizQuestions(new ArrayList<>(quizQuestions.stream().map(QuizQuestionCreateDTO::toDomainObject).toList()));
        quizExercise.setAllowedNumberOfAttempts(1);
        return quizExercise;
    }
}
