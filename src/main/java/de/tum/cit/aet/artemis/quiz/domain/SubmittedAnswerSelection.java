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
// The type discriminator and all selection keys are abbreviated (dnd/sa/mc, and short field keys on the subtypes) to save space: submitted_answer.selection has one row per
// submitted
// answer (millions at scale, mostly below the TOAST compression threshold). This is safe because the selection subtypes are never serialized to a client — the wire uses the
// resolved objects returned by getMappings()/getSubmittedTexts()/getSelectedOptions(). See documentation/docs/developer/guidelines/quiz-json-persistence-migration.mdx for the
// legend.
@JsonSubTypes({ @JsonSubTypes.Type(value = DragAndDropSubmittedAnswerSelection.class, name = "dnd"),
        @JsonSubTypes.Type(value = ShortAnswerSubmittedAnswerSelection.class, name = "sa"), @JsonSubTypes.Type(value = MultipleChoiceSubmittedAnswerSelection.class, name = "mc") })
public sealed interface SubmittedAnswerSelection permits DragAndDropSubmittedAnswerSelection, ShortAnswerSubmittedAnswerSelection, MultipleChoiceSubmittedAnswerSelection {
}
