package de.tum.cit.aet.artemis.quiz.domain;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Type-specific "correct answer" content of a {@link QuizQuestion}, stored as a JSON column ({@code quiz_question.content}) instead of separate relational child tables.
 * <p>
 * This replaces the former {@code @OneToMany(fetch = EAGER)} child collections (drop locations, drag items, correct mappings, answer options, short-answer
 * spots/solutions/mappings)
 * that caused a Cartesian-product fan-out when loading a quiz with many questions. Each question type stores its own subtype; the {@link JsonTypeInfo} discriminator lets Jackson
 * (de)serialize the polymorphic value into/out of the column.
 * <p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = DragAndDropQuestionContent.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerQuestionContent.class, name = "short-answer"),
        @JsonSubTypes.Type(value = MultipleChoiceQuestionContent.class, name = "multiple-choice") })
public sealed interface QuizQuestionContent permits DragAndDropQuestionContent, ShortAnswerQuestionContent, MultipleChoiceQuestionContent {

    /**
     * @return the ids of all components (drop locations, drag items, mappings, ...) contained in this content. Used to mint fresh, question-scoped ids for newly added components
     *         via {@code max(componentIds) + 1} and to validate id uniqueness within a question.
     */
    Set<Long> componentIds();
}
