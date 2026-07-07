package de.tum.cit.aet.artemis.quiz.dto;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.AnswerCounter;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.DropLocationCounter;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpotCounter;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionStatisticDTO(Long id, Integer participantsRated, Integer participantsUnrated, Integer ratedCorrectCounter, Integer unRatedCorrectCounter,
        @JsonUnwrapped MultipleChoiceQuestionStatisticDTO multipleChoiceQuestionStatisticDTO, @JsonUnwrapped DragAndDropQuestionStatisticDTO dragAndDropQuestionStatisticDTO,
        @JsonUnwrapped ShortAnswerQuestionStatisticDTO shortAnswerQuestionStatisticDTO, String type) {

    /**
     * Converts a QuizQuestionStatistic entity to a QuizQuestionStatisticDTO.
     *
     * @param quizQuestionStatistic the entity to convert
     * @return the converted DTO
     */
    public static QuizQuestionStatisticDTO of(QuizQuestionStatistic quizQuestionStatistic) {
        MultipleChoiceQuestionStatisticDTO multipleChoiceQuestionStatisticDTO = null;
        DragAndDropQuestionStatisticDTO dragAndDropQuestionStatisticDTO = null;
        ShortAnswerQuestionStatisticDTO shortAnswerQuestionStatisticDTO = null;
        String type = null;

        if (quizQuestionStatistic instanceof MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic) {
            multipleChoiceQuestionStatisticDTO = MultipleChoiceQuestionStatisticDTO.of(multipleChoiceQuestionStatistic);
            type = "multiple-choice";
        }
        else if (quizQuestionStatistic instanceof DragAndDropQuestionStatistic dragAndDropQuestionStatistic) {
            dragAndDropQuestionStatisticDTO = DragAndDropQuestionStatisticDTO.of(dragAndDropQuestionStatistic);
            type = "drag-and-drop";
        }
        else if (quizQuestionStatistic instanceof ShortAnswerQuestionStatistic shortAnswerQuestionStatistic) {
            shortAnswerQuestionStatisticDTO = ShortAnswerQuestionStatisticDTO.of(shortAnswerQuestionStatistic);
            type = "short-answer";
        }

        return new QuizQuestionStatisticDTO(quizQuestionStatistic.getId(), quizQuestionStatistic.getParticipantsRated(), quizQuestionStatistic.getParticipantsUnrated(),
                quizQuestionStatistic.getRatedCorrectCounter(), quizQuestionStatistic.getUnRatedCorrectCounter(), multipleChoiceQuestionStatisticDTO,
                dragAndDropQuestionStatisticDTO, shortAnswerQuestionStatisticDTO, type);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record MultipleChoiceQuestionStatisticDTO(Set<AnswerCounterDTO> answerCounters) {

    public static MultipleChoiceQuestionStatisticDTO of(MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic) {
        MultipleChoiceQuestion question = multipleChoiceQuestionStatistic.getQuizQuestion() instanceof MultipleChoiceQuestion multipleChoiceQuestion ? multipleChoiceQuestion
                : null;
        return new MultipleChoiceQuestionStatisticDTO(
                multipleChoiceQuestionStatistic.getAnswerCounters().stream().map(counter -> AnswerCounterDTO.of(counter, question)).collect(Collectors.toSet()));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DragAndDropQuestionStatisticDTO(Set<DropLocationCounterDTO> dropLocationCounters) {

    public static DragAndDropQuestionStatisticDTO of(DragAndDropQuestionStatistic dragAndDropQuestionStatistic) {
        DragAndDropQuestion question = dragAndDropQuestionStatistic.getQuizQuestion() instanceof DragAndDropQuestion dragAndDropQuestion ? dragAndDropQuestion : null;
        return new DragAndDropQuestionStatisticDTO(
                dragAndDropQuestionStatistic.getDropLocationCounters().stream().map(counter -> DropLocationCounterDTO.of(counter, question)).collect(Collectors.toSet()));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerQuestionStatisticDTO(Set<ShortAnswerSpotCounterDTO> shortAnswerSpotCounters) {

    public static ShortAnswerQuestionStatisticDTO of(ShortAnswerQuestionStatistic shortAnswerQuestionStatistic) {
        ShortAnswerQuestion question = shortAnswerQuestionStatistic.getQuizQuestion() instanceof ShortAnswerQuestion shortAnswerQuestion ? shortAnswerQuestion : null;
        return new ShortAnswerQuestionStatisticDTO(
                shortAnswerQuestionStatistic.getShortAnswerSpotCounters().stream().map(counter -> ShortAnswerSpotCounterDTO.of(counter, question)).collect(Collectors.toSet()));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerSpotCounterDTO(ShortAnswerSpotDTO spot, @JsonUnwrapped QuizStatisticCounterDTO quizStatisticCounter) {

    public static ShortAnswerSpotCounterDTO of(ShortAnswerSpotCounter shortAnswerSpotCounter, ShortAnswerQuestion question) {
        ShortAnswerSpot spot = question != null ? question.findSpotById(shortAnswerSpotCounter.getSpotId()) : null;
        QuizStatisticCounterDTO counterDTO = new QuizStatisticCounterDTO(null, shortAnswerSpotCounter.getRatedCounter(), shortAnswerSpotCounter.getUnRatedCounter());
        return new ShortAnswerSpotCounterDTO(spot != null ? ShortAnswerSpotDTO.of(spot) : null, counterDTO);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DropLocationCounterDTO(DropLocationDTO dropLocation, @JsonUnwrapped QuizStatisticCounterDTO quizStatisticCounter) {

    public static DropLocationCounterDTO of(DropLocationCounter dropLocationCounter, DragAndDropQuestion question) {
        DropLocation dropLocation = question != null ? question.findDropLocationById(dropLocationCounter.getDropLocationId()) : null;
        QuizStatisticCounterDTO counterDTO = new QuizStatisticCounterDTO(null, dropLocationCounter.getRatedCounter(), dropLocationCounter.getUnRatedCounter());
        return new DropLocationCounterDTO(dropLocation != null ? DropLocationDTO.of(dropLocation) : null, counterDTO);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record AnswerCounterDTO(AnswerOptionWithSolutionDTO answer, @JsonUnwrapped QuizStatisticCounterDTO quizStatisticCounter) {

    public static AnswerCounterDTO of(AnswerCounter answerCounter, MultipleChoiceQuestion question) {
        AnswerOption answer = question != null ? question.findAnswerOptionById(answerCounter.getAnswerId()) : null;
        QuizStatisticCounterDTO counterDTO = new QuizStatisticCounterDTO(null, answerCounter.getRatedCounter(), answerCounter.getUnRatedCounter());
        return new AnswerCounterDTO(answer != null ? AnswerOptionWithSolutionDTO.of(answer) : null, counterDTO);
    }
}
