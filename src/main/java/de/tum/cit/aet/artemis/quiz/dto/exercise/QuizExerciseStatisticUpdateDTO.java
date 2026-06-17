package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.AnswerCounter;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.DropLocationCounter;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpotCounter;
import de.tum.cit.aet.artemis.quiz.dto.AnswerOptionWithoutSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.DropLocationDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizStatisticCounterDTO;
import de.tum.cit.aet.artemis.quiz.dto.ShortAnswerSpotDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithoutSolutionDTO;

/**
 * Minimal solution-free payload sent when quiz statistics change.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseStatisticUpdateDTO(Long id, List<QuizQuestionStatisticUpdateDTO> quizQuestions, QuizPointStatisticDTO quizPointStatistic) {

    public static QuizExerciseStatisticUpdateDTO of(QuizExercise quizExercise) {
        List<QuizQuestionStatisticUpdateDTO> questionDTOs = quizExercise.getQuizQuestions().stream().map(QuizQuestionStatisticUpdateDTO::of).toList();
        QuizPointStatisticDTO pointStatisticDTO = quizExercise.getQuizPointStatistic() == null ? null : QuizPointStatisticDTO.of(quizExercise.getQuizPointStatistic());
        return new QuizExerciseStatisticUpdateDTO(quizExercise.getId(), questionDTOs, pointStatisticDTO);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuizQuestionStatisticUpdateDTO(@JsonUnwrapped QuizQuestionWithoutSolutionDTO question, QuizQuestionStatisticWithoutSolutionDTO quizQuestionStatistic) {

    public static QuizQuestionStatisticUpdateDTO of(QuizQuestion question) {
        QuizQuestionStatisticWithoutSolutionDTO statisticDTO = question.getQuizQuestionStatistic() == null ? null
                : QuizQuestionStatisticWithoutSolutionDTO.of(question.getQuizQuestionStatistic());
        return new QuizQuestionStatisticUpdateDTO(QuizQuestionWithoutSolutionDTO.of(question), statisticDTO);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuizQuestionStatisticWithoutSolutionDTO(Long id, Integer participantsRated, Integer participantsUnrated, Integer ratedCorrectCounter, Integer unRatedCorrectCounter,
        @JsonUnwrapped MultipleChoiceQuestionStatisticWithoutSolutionDTO multipleChoiceQuestionStatistic,
        @JsonUnwrapped DragAndDropQuestionStatisticWithoutSolutionDTO dragAndDropQuestionStatistic,
        @JsonUnwrapped ShortAnswerQuestionStatisticWithoutSolutionDTO shortAnswerQuestionStatistic, String type) {

    public static QuizQuestionStatisticWithoutSolutionDTO of(QuizQuestionStatistic statistic) {
        MultipleChoiceQuestionStatisticWithoutSolutionDTO multipleChoiceStatisticDTO = null;
        DragAndDropQuestionStatisticWithoutSolutionDTO dragAndDropStatisticDTO = null;
        ShortAnswerQuestionStatisticWithoutSolutionDTO shortAnswerStatisticDTO = null;
        String type = null;

        if (statistic instanceof MultipleChoiceQuestionStatistic multipleChoiceStatistic) {
            multipleChoiceStatisticDTO = MultipleChoiceQuestionStatisticWithoutSolutionDTO.of(multipleChoiceStatistic);
            type = "multiple-choice";
        }
        else if (statistic instanceof DragAndDropQuestionStatistic dragAndDropStatistic) {
            dragAndDropStatisticDTO = DragAndDropQuestionStatisticWithoutSolutionDTO.of(dragAndDropStatistic);
            type = "drag-and-drop";
        }
        else if (statistic instanceof ShortAnswerQuestionStatistic shortAnswerStatistic) {
            shortAnswerStatisticDTO = ShortAnswerQuestionStatisticWithoutSolutionDTO.of(shortAnswerStatistic);
            type = "short-answer";
        }

        return new QuizQuestionStatisticWithoutSolutionDTO(statistic.getId(), statistic.getParticipantsRated(), statistic.getParticipantsUnrated(),
                statistic.getRatedCorrectCounter(), statistic.getUnRatedCorrectCounter(), multipleChoiceStatisticDTO, dragAndDropStatisticDTO, shortAnswerStatisticDTO, type);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record MultipleChoiceQuestionStatisticWithoutSolutionDTO(Set<AnswerCounterWithoutSolutionDTO> answerCounters) {

    public static MultipleChoiceQuestionStatisticWithoutSolutionDTO of(MultipleChoiceQuestionStatistic statistic) {
        return new MultipleChoiceQuestionStatisticWithoutSolutionDTO(statistic.getAnswerCounters().stream().map(AnswerCounterWithoutSolutionDTO::of).collect(Collectors.toSet()));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record AnswerCounterWithoutSolutionDTO(AnswerOptionWithoutSolutionDTO answer, @JsonUnwrapped QuizStatisticCounterDTO counter) {

    public static AnswerCounterWithoutSolutionDTO of(AnswerCounter answerCounter) {
        return new AnswerCounterWithoutSolutionDTO(AnswerOptionWithoutSolutionDTO.of(answerCounter.getAnswer()), QuizStatisticCounterDTO.of(answerCounter));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DragAndDropQuestionStatisticWithoutSolutionDTO(Set<DropLocationCounterWithoutSolutionDTO> dropLocationCounters) {

    public static DragAndDropQuestionStatisticWithoutSolutionDTO of(DragAndDropQuestionStatistic statistic) {
        return new DragAndDropQuestionStatisticWithoutSolutionDTO(
                statistic.getDropLocationCounters().stream().map(DropLocationCounterWithoutSolutionDTO::of).collect(Collectors.toSet()));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DropLocationCounterWithoutSolutionDTO(DropLocationDTO dropLocation, @JsonUnwrapped QuizStatisticCounterDTO counter) {

    public static DropLocationCounterWithoutSolutionDTO of(DropLocationCounter counter) {
        return new DropLocationCounterWithoutSolutionDTO(DropLocationDTO.of(counter.getDropLocation()), QuizStatisticCounterDTO.of(counter));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerQuestionStatisticWithoutSolutionDTO(Set<ShortAnswerSpotCounterWithoutSolutionDTO> shortAnswerSpotCounters) {

    public static ShortAnswerQuestionStatisticWithoutSolutionDTO of(ShortAnswerQuestionStatistic statistic) {
        return new ShortAnswerQuestionStatisticWithoutSolutionDTO(
                statistic.getShortAnswerSpotCounters().stream().map(ShortAnswerSpotCounterWithoutSolutionDTO::of).collect(Collectors.toSet()));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerSpotCounterWithoutSolutionDTO(ShortAnswerSpotDTO spot, @JsonUnwrapped QuizStatisticCounterDTO counter) {

    public static ShortAnswerSpotCounterWithoutSolutionDTO of(ShortAnswerSpotCounter counter) {
        return new ShortAnswerSpotCounterWithoutSolutionDTO(ShortAnswerSpotDTO.of(counter.getSpot()), QuizStatisticCounterDTO.of(counter));
    }
}
