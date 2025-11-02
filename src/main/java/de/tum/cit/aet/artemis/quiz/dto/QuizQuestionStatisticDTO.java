package de.tum.cit.aet.artemis.quiz.dto;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.AnswerCounter;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.DropLocationCounter;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpotCounter;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionStatisticDTO(Long id, Integer participantsRated, Integer participantsUnrated, Integer ratedCorrectCounter, Integer unRatedCorrectCounter,
        @Nullable @JsonUnwrapped MultipleChoiceQuestionStatisticDTO multipleChoiceQuestionStatisticDTO,
        @Nullable @JsonUnwrapped DragAndDropQuestionStatisticDTO dragAndDropQuestionStatisticDTO,
        @Nullable @JsonUnwrapped ShortAnswerQuestionStatisticDTO shortAnswerQuestionStatisticDTO, String type) {

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
record MultipleChoiceQuestionStatisticDTO(Set<AnswerCounterDTO> answer) {

    public static MultipleChoiceQuestionStatisticDTO of(MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic) {
        return new MultipleChoiceQuestionStatisticDTO(multipleChoiceQuestionStatistic.getAnswerCounters().stream().map(AnswerCounterDTO::of).collect(Collectors.toSet()));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DragAndDropQuestionStatisticDTO(Set<DropLocationCounterDTO> dropLocationCounters) {

    public static DragAndDropQuestionStatisticDTO of(DragAndDropQuestionStatistic dragAndDropQuestionStatistic) {
        return new DragAndDropQuestionStatisticDTO(dragAndDropQuestionStatistic.getDropLocationCounters().stream().map(DropLocationCounterDTO::of).collect(Collectors.toSet()));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerQuestionStatisticDTO(Set<ShortAnswerSpotCounterDTO> shortAnswerSpotCounters) {

    public static ShortAnswerQuestionStatisticDTO of(ShortAnswerQuestionStatistic shortAnswerQuestionStatistic) {
        return new ShortAnswerQuestionStatisticDTO(
                shortAnswerQuestionStatistic.getShortAnswerSpotCounters().stream().map(ShortAnswerSpotCounterDTO::of).collect(Collectors.toSet()));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerSpotCounterDTO(ShortAnswerSpotDTO spot, @JsonUnwrapped QuizStatisticCounterDTO quizStatisticCounter) {

    public static ShortAnswerSpotCounterDTO of(ShortAnswerSpotCounter shortAnswerSpotCounter) {
        return new ShortAnswerSpotCounterDTO(ShortAnswerSpotDTO.of(shortAnswerSpotCounter.getSpot()), QuizStatisticCounterDTO.of(shortAnswerSpotCounter));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DropLocationCounterDTO(DropLocationDTO dropLocation, @JsonUnwrapped QuizStatisticCounterDTO quizStatisticCounter) {

    public static DropLocationCounterDTO of(DropLocationCounter dropLocationCounter) {
        return new DropLocationCounterDTO(DropLocationDTO.of(dropLocationCounter.getDropLocation()), QuizStatisticCounterDTO.of(dropLocationCounter));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record AnswerCounterDTO(AnswerOptionWithSolutionDTO answer, @JsonUnwrapped QuizStatisticCounterDTO quizStatisticCounter) {

    public static AnswerCounterDTO of(AnswerCounter answerCounter) {
        return new AnswerCounterDTO(AnswerOptionWithSolutionDTO.of(answerCounter.getAnswer()), QuizStatisticCounterDTO.of(answerCounter));
    }
}
