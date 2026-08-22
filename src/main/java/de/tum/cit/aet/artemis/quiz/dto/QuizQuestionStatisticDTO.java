package de.tum.cit.aet.artemis.quiz.dto;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;

/**
 * Question statistics calculated on demand from results and submitted-answer selections.
 * Participant counts are per rating bucket: one participation can contribute its latest rated result and its latest unrated result.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionStatisticDTO(Long id, Integer participantsRated, Integer participantsUnrated, Integer ratedCorrectCounter, Integer unRatedCorrectCounter,
        @JsonUnwrapped MultipleChoiceQuestionStatisticDTO multipleChoiceQuestionStatisticDTO, @JsonUnwrapped DragAndDropQuestionStatisticDTO dragAndDropQuestionStatisticDTO,
        @JsonUnwrapped ShortAnswerQuestionStatisticDTO shortAnswerQuestionStatisticDTO, String type) {

    /**
     * Creates the wire-compatible statistic for a quiz question.
     *
     * @param question          the question that determines the statistic type
     * @param counters          participant and correctness counters in rated/unrated order
     * @param componentCounters counters keyed by answer option, drop location, or short-answer spot id
     * @return the question statistic
     */
    public static QuizQuestionStatisticDTO of(QuizQuestion question, long[] counters, Map<Long, long[]> componentCounters) {
        MultipleChoiceQuestionStatisticDTO multipleChoiceStatistic = null;
        DragAndDropQuestionStatisticDTO dragAndDropStatistic = null;
        ShortAnswerQuestionStatisticDTO shortAnswerStatistic = null;
        String type;

        switch (question) {
            case MultipleChoiceQuestion ignored -> {
                type = "multiple-choice";
                if (componentCounters != null) {
                    multipleChoiceStatistic = new MultipleChoiceQuestionStatisticDTO(componentCounters.entrySet().stream()
                            .map(entry -> new AnswerCounterDTO(entry.getKey(), QuizStatisticCounterDTO.of(entry.getValue()))).collect(Collectors.toSet()));
                }
            }
            case DragAndDropQuestion ignored -> {
                type = "drag-and-drop";
                if (componentCounters != null) {
                    dragAndDropStatistic = new DragAndDropQuestionStatisticDTO(componentCounters.entrySet().stream()
                            .map(entry -> new DropLocationCounterDTO(entry.getKey(), QuizStatisticCounterDTO.of(entry.getValue()))).collect(Collectors.toSet()));
                }
            }
            case ShortAnswerQuestion ignored -> {
                type = "short-answer";
                if (componentCounters != null) {
                    shortAnswerStatistic = new ShortAnswerQuestionStatisticDTO(componentCounters.entrySet().stream()
                            .map(entry -> new ShortAnswerSpotCounterDTO(entry.getKey(), QuizStatisticCounterDTO.of(entry.getValue()))).collect(Collectors.toSet()));
                }
            }
            default -> throw new IllegalArgumentException("Unsupported quiz question type " + question.getClass().getName());
        }

        return new QuizQuestionStatisticDTO(null, Math.toIntExact(counters[0]), Math.toIntExact(counters[1]), Math.toIntExact(counters[2]), Math.toIntExact(counters[3]),
                multipleChoiceStatistic, dragAndDropStatistic, shortAnswerStatistic, type);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record MultipleChoiceQuestionStatisticDTO(Set<AnswerCounterDTO> answerCounters) {
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DragAndDropQuestionStatisticDTO(Set<DropLocationCounterDTO> dropLocationCounters) {
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerQuestionStatisticDTO(Set<ShortAnswerSpotCounterDTO> shortAnswerSpotCounters) {
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerSpotCounterDTO(Long spotId, @JsonUnwrapped QuizStatisticCounterDTO quizStatisticCounter) {
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DropLocationCounterDTO(Long dropLocationId, @JsonUnwrapped QuizStatisticCounterDTO quizStatisticCounter) {
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record AnswerCounterDTO(Long answerId, @JsonUnwrapped QuizStatisticCounterDTO quizStatisticCounter) {
}
