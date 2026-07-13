package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.AnswerCounter;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.DropLocationCounter;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpotCounter;
import de.tum.cit.aet.artemis.quiz.dto.AnswerOptionWithoutSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.DropLocationDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizStatisticCounterDTO;
import de.tum.cit.aet.artemis.quiz.dto.ShortAnswerSpotDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithoutSolutionDTO;

/**
 * Solution-free payload sent when persisted quiz statistics change.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseStatisticUpdateDTO(Long id, List<QuizQuestionStatisticUpdateDTO> quizQuestions, QuizPointStatisticDTO quizPointStatistic) {

    /**
     * Creates a statistic update without mutating the underlying quiz aggregate.
     *
     * @param quizExercise the quiz whose statistics changed
     * @return the solution-free statistic update
     */
    public static QuizExerciseStatisticUpdateDTO of(QuizExercise quizExercise) {
        List<QuizQuestionStatisticUpdateDTO> questionDTOs = quizExercise.getQuizQuestions().stream().map(QuizQuestionStatisticUpdateDTO::of).toList();
        QuizPointStatisticDTO pointStatisticDTO = quizExercise.getQuizPointStatistic() == null ? null : QuizPointStatisticDTO.of(quizExercise.getQuizPointStatistic());
        return new QuizExerciseStatisticUpdateDTO(quizExercise.getId(), questionDTOs, pointStatisticDTO);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuizQuestionStatisticUpdateDTO(@JsonUnwrapped QuizQuestionWithoutSolutionDTO question, QuizQuestionStatisticWithoutSolutionDTO quizQuestionStatistic) {

    static QuizQuestionStatisticUpdateDTO of(QuizQuestion question) {
        QuizQuestionStatisticWithoutSolutionDTO statisticDTO = question.getQuizQuestionStatistic() == null ? null
                : QuizQuestionStatisticWithoutSolutionDTO.of(question.getQuizQuestionStatistic(), question);
        return new QuizQuestionStatisticUpdateDTO(QuizQuestionWithoutSolutionDTO.of(question), statisticDTO);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuizQuestionStatisticWithoutSolutionDTO(Long id, Integer participantsRated, Integer participantsUnrated, Integer ratedCorrectCounter, Integer unRatedCorrectCounter,
        @JsonUnwrapped MultipleChoiceQuestionStatisticWithoutSolutionDTO multipleChoiceQuestionStatistic,
        @JsonUnwrapped DragAndDropQuestionStatisticWithoutSolutionDTO dragAndDropQuestionStatistic,
        @JsonUnwrapped ShortAnswerQuestionStatisticWithoutSolutionDTO shortAnswerQuestionStatistic, String type) {

    static QuizQuestionStatisticWithoutSolutionDTO of(QuizQuestionStatistic statistic, QuizQuestion question) {
        MultipleChoiceQuestionStatisticWithoutSolutionDTO multipleChoiceStatisticDTO = null;
        DragAndDropQuestionStatisticWithoutSolutionDTO dragAndDropStatisticDTO = null;
        ShortAnswerQuestionStatisticWithoutSolutionDTO shortAnswerStatisticDTO = null;
        String type = null;

        if (statistic instanceof MultipleChoiceQuestionStatistic multipleChoiceStatistic) {
            multipleChoiceStatisticDTO = MultipleChoiceQuestionStatisticWithoutSolutionDTO.of(multipleChoiceStatistic, (MultipleChoiceQuestion) question);
            type = "multiple-choice";
        }
        else if (statistic instanceof DragAndDropQuestionStatistic dragAndDropStatistic) {
            dragAndDropStatisticDTO = DragAndDropQuestionStatisticWithoutSolutionDTO.of(dragAndDropStatistic, (DragAndDropQuestion) question);
            type = "drag-and-drop";
        }
        else if (statistic instanceof ShortAnswerQuestionStatistic shortAnswerStatistic) {
            shortAnswerStatisticDTO = ShortAnswerQuestionStatisticWithoutSolutionDTO.of(shortAnswerStatistic, (ShortAnswerQuestion) question);
            type = "short-answer";
        }

        return new QuizQuestionStatisticWithoutSolutionDTO(statistic.getId(), statistic.getParticipantsRated(), statistic.getParticipantsUnrated(),
                statistic.getRatedCorrectCounter(), statistic.getUnRatedCorrectCounter(), multipleChoiceStatisticDTO, dragAndDropStatisticDTO, shortAnswerStatisticDTO, type);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record MultipleChoiceQuestionStatisticWithoutSolutionDTO(List<AnswerCounterWithoutSolutionDTO> answerCounters) {

    static MultipleChoiceQuestionStatisticWithoutSolutionDTO of(MultipleChoiceQuestionStatistic statistic, MultipleChoiceQuestion question) {
        return new MultipleChoiceQuestionStatisticWithoutSolutionDTO(
                statistic.getAnswerCounters().stream().map(counter -> AnswerCounterWithoutSolutionDTO.of(counter, question)).toList());
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record AnswerCounterWithoutSolutionDTO(AnswerOptionWithoutSolutionDTO answer, @JsonUnwrapped QuizStatisticCounterDTO counter) {

    static AnswerCounterWithoutSolutionDTO of(AnswerCounter answerCounter, MultipleChoiceQuestion question) {
        QuizStatisticCounterDTO counterDTO = new QuizStatisticCounterDTO(null, answerCounter.getRatedCounter(), answerCounter.getUnRatedCounter());
        return new AnswerCounterWithoutSolutionDTO(AnswerOptionWithoutSolutionDTO.of(question.findAnswerOptionById(answerCounter.getAnswerId())), counterDTO);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DragAndDropQuestionStatisticWithoutSolutionDTO(List<DropLocationCounterWithoutSolutionDTO> dropLocationCounters) {

    static DragAndDropQuestionStatisticWithoutSolutionDTO of(DragAndDropQuestionStatistic statistic, DragAndDropQuestion question) {
        return new DragAndDropQuestionStatisticWithoutSolutionDTO(
                statistic.getDropLocationCounters().stream().map(counter -> DropLocationCounterWithoutSolutionDTO.of(counter, question)).toList());
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DropLocationCounterWithoutSolutionDTO(DropLocationDTO dropLocation, @JsonUnwrapped QuizStatisticCounterDTO counter) {

    static DropLocationCounterWithoutSolutionDTO of(DropLocationCounter counter, DragAndDropQuestion question) {
        QuizStatisticCounterDTO counterDTO = new QuizStatisticCounterDTO(null, counter.getRatedCounter(), counter.getUnRatedCounter());
        return new DropLocationCounterWithoutSolutionDTO(DropLocationDTO.of(question.findDropLocationById(counter.getDropLocationId())), counterDTO);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerQuestionStatisticWithoutSolutionDTO(List<ShortAnswerSpotCounterWithoutSolutionDTO> shortAnswerSpotCounters) {

    static ShortAnswerQuestionStatisticWithoutSolutionDTO of(ShortAnswerQuestionStatistic statistic, ShortAnswerQuestion question) {
        return new ShortAnswerQuestionStatisticWithoutSolutionDTO(
                statistic.getShortAnswerSpotCounters().stream().map(counter -> ShortAnswerSpotCounterWithoutSolutionDTO.of(counter, question)).toList());
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerSpotCounterWithoutSolutionDTO(ShortAnswerSpotDTO spot, @JsonUnwrapped QuizStatisticCounterDTO counter) {

    static ShortAnswerSpotCounterWithoutSolutionDTO of(ShortAnswerSpotCounter counter, ShortAnswerQuestion question) {
        QuizStatisticCounterDTO counterDTO = new QuizStatisticCounterDTO(null, counter.getRatedCounter(), counter.getUnRatedCounter());
        return new ShortAnswerSpotCounterWithoutSolutionDTO(ShortAnswerSpotDTO.of(question.findSpotById(counter.getSpotId())), counterDTO);
    }
}
