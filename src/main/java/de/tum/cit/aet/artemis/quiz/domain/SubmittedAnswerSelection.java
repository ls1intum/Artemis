package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A student's submitted selection for a {@link SubmittedAnswer}, stored as a JSON column ({@code submitted_answer.selection}) instead of separate relational child tables/join
 * tables.
 * <p>
 * This replaces the former {@code @OneToMany}/{@code @ManyToMany} submission collections (drag-and-drop mappings, selected answer options, short-answer submitted texts). Each
 * answer
 * type stores its own subtype; the {@link JsonTypeInfo} discriminator lets Jackson (de)serialize the polymorphic value into/out of the column.
 * <p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = DragAndDropSubmittedAnswerSelection.class, name = "drag-and-drop"),
        @JsonSubTypes.Type(value = ShortAnswerSubmittedAnswerSelection.class, name = "short-answer"),
        @JsonSubTypes.Type(value = MultipleChoiceSubmittedAnswerSelection.class, name = "multiple-choice") })
public sealed interface SubmittedAnswerSelection permits DragAndDropSubmittedAnswerSelection, ShortAnswerSubmittedAnswerSelection, MultipleChoiceSubmittedAnswerSelection {
}
