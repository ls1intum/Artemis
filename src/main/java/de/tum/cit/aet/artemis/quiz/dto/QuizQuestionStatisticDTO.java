package de.tum.cit.aet.artemis.quiz.dto;

import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Question statistics calculated on demand from results and submitted-answer selections.
 * Participant counts are per rating bucket: one participation can contribute its latest rated result and its latest unrated result.
 */
@Schema(discriminatorProperty = "type", discriminatorMapping = { @DiscriminatorMapping(value = "multiple-choice", schema = MultipleChoiceQuestionStatisticDTO.class),
        @DiscriminatorMapping(value = "drag-and-drop", schema = DragAndDropQuestionStatisticDTO.class),
        @DiscriminatorMapping(value = "short-answer", schema = ShortAnswerQuestionStatisticDTO.class) }, oneOf = { MultipleChoiceQuestionStatisticDTO.class,
                DragAndDropQuestionStatisticDTO.class, ShortAnswerQuestionStatisticDTO.class })
@JsonSubTypes({ @JsonSubTypes.Type(value = MultipleChoiceQuestionStatisticDTO.class, name = "multiple-choice"),
        @JsonSubTypes.Type(value = DragAndDropQuestionStatisticDTO.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerQuestionStatisticDTO.class, name = "short-answer") })
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface QuizQuestionStatisticDTO permits MultipleChoiceQuestionStatisticDTO, DragAndDropQuestionStatisticDTO, ShortAnswerQuestionStatisticDTO {

    Integer participantsRated();

    Integer participantsUnrated();

    Integer ratedCorrectCounter();

    Integer unRatedCorrectCounter();

    /**
     * Creates the statistic for a quiz question.
     *
     * @param question            the question that determines the statistic type
     * @param ratedParticipants   the rated participant count
     * @param unratedParticipants the unrated participant count
     * @param ratedCorrect        the rated fully-correct count
     * @param unratedCorrect      the unrated fully-correct count
     * @param componentStatistics counters keyed by answer option, drop location, or short-answer spot id
     * @return the question statistic
     */
    static QuizQuestionStatisticDTO of(QuizQuestion question, long ratedParticipants, long unratedParticipants, long ratedCorrect, long unratedCorrect,
            Map<Long, QuizStatisticCounterDTO> componentStatistics) {
        Integer participantsRated = Math.toIntExact(ratedParticipants);
        Integer participantsUnrated = Math.toIntExact(unratedParticipants);
        Integer ratedCorrectCounter = Math.toIntExact(ratedCorrect);
        Integer unRatedCorrectCounter = Math.toIntExact(unratedCorrect);

        return switch (question) {
            case MultipleChoiceQuestion ignored ->
                new MultipleChoiceQuestionStatisticDTO(participantsRated, participantsUnrated, ratedCorrectCounter, unRatedCorrectCounter, componentStatistics == null ? null
                        : componentStatistics.entrySet().stream().map(entry -> new AnswerCounterDTO(entry.getKey(), entry.getValue())).collect(Collectors.toSet()));
            case DragAndDropQuestion ignored ->
                new DragAndDropQuestionStatisticDTO(participantsRated, participantsUnrated, ratedCorrectCounter, unRatedCorrectCounter, componentStatistics == null ? null
                        : componentStatistics.entrySet().stream().map(entry -> new DropLocationCounterDTO(entry.getKey(), entry.getValue())).collect(Collectors.toSet()));
            case ShortAnswerQuestion ignored ->
                new ShortAnswerQuestionStatisticDTO(participantsRated, participantsUnrated, ratedCorrectCounter, unRatedCorrectCounter, componentStatistics == null ? null
                        : componentStatistics.entrySet().stream().map(entry -> new ShortAnswerSpotCounterDTO(entry.getKey(), entry.getValue())).collect(Collectors.toSet()));
            default -> throw new IllegalArgumentException("Unsupported quiz question type " + question.getClass().getName());
        };
    }
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
