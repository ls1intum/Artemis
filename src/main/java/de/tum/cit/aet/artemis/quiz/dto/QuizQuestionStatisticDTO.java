package de.tum.cit.aet.artemis.quiz.dto;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.AnswerCounter;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.DropLocationCounter;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpotCounter;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

@Schema(discriminatorProperty = "type", discriminatorMapping = { @DiscriminatorMapping(value = "multiple-choice", schema = MultipleChoiceQuizQuestionStatisticDTO.class),
        @DiscriminatorMapping(value = "drag-and-drop", schema = DragAndDropQuizQuestionStatisticDTO.class),
        @DiscriminatorMapping(value = "short-answer", schema = ShortAnswerQuizQuestionStatisticDTO.class) }, oneOf = { MultipleChoiceQuizQuestionStatisticDTO.class,
                DragAndDropQuizQuestionStatisticDTO.class, ShortAnswerQuizQuestionStatisticDTO.class })

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

// These definitions are used for OpenAPI generation because polymorphic types with @JsonUnwrapped do not work here
@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "multiple-choice" }, defaultValue = "multiple-choice"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record MultipleChoiceQuizQuestionStatisticDTO(Long id, Integer participantsRated, Integer participantsUnrated, Integer ratedCorrectCounter, Integer unRatedCorrectCounter,
        @JsonUnwrapped MultipleChoiceQuestionStatisticDTO multipleChoiceQuestionStatisticDTO) {
}

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "drag-and-drop" }, defaultValue = "drag-and-drop"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DragAndDropQuizQuestionStatisticDTO(Long id, Integer participantsRated, Integer participantsUnrated, Integer ratedCorrectCounter, Integer unRatedCorrectCounter,
        @JsonUnwrapped DragAndDropQuestionStatisticDTO dragAndDropQuestionStatisticDTO) {
}

@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "short-answer" }, defaultValue = "short-answer"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerQuizQuestionStatisticDTO(Long id, Integer participantsRated, Integer participantsUnrated, Integer ratedCorrectCounter, Integer unRatedCorrectCounter,
        @JsonUnwrapped ShortAnswerQuestionStatisticDTO shortAnswerQuestionStatisticDTO) {
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record MultipleChoiceQuestionStatisticDTO(Set<AnswerCounterDTO> answerCounters) {

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
record ShortAnswerSpotCounterDTO(Long spotId, @JsonUnwrapped QuizStatisticCounterDTO quizStatisticCounter) {

    public static ShortAnswerSpotCounterDTO of(ShortAnswerSpotCounter shortAnswerSpotCounter) {
        QuizStatisticCounterDTO counterDTO = new QuizStatisticCounterDTO(null, shortAnswerSpotCounter.getRatedCounter(), shortAnswerSpotCounter.getUnRatedCounter());
        return new ShortAnswerSpotCounterDTO(shortAnswerSpotCounter.getSpotId(), counterDTO);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DropLocationCounterDTO(Long dropLocationId, @JsonUnwrapped QuizStatisticCounterDTO quizStatisticCounter) {

    public static DropLocationCounterDTO of(DropLocationCounter dropLocationCounter) {
        QuizStatisticCounterDTO counterDTO = new QuizStatisticCounterDTO(null, dropLocationCounter.getRatedCounter(), dropLocationCounter.getUnRatedCounter());
        return new DropLocationCounterDTO(dropLocationCounter.getDropLocationId(), counterDTO);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record AnswerCounterDTO(Long answerId, @JsonUnwrapped QuizStatisticCounterDTO quizStatisticCounter) {

    public static AnswerCounterDTO of(AnswerCounter answerCounter) {
        QuizStatisticCounterDTO counterDTO = new QuizStatisticCounterDTO(null, answerCounter.getRatedCounter(), answerCounter.getUnRatedCounter());
        return new AnswerCounterDTO(answerCounter.getAnswerId(), counterDTO);
    }
}
