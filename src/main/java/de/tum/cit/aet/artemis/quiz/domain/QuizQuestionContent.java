package de.tum.cit.aet.artemis.quiz.domain;

import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

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

    Logger log = LoggerFactory.getLogger(QuizQuestionContent.class);

    /**
     * Serializer used only for the value comparison below. It is separate from the application's Jackson setup on
     * purpose: this must stay a stable, dependency-free rendering of the content, because its only job is to decide
     * whether two content values would be written to the column identically.
     */
    JsonMapper COMPARISON_MAPPER = new JsonMapper();

    /**
     * @return the ids of all components (drop locations, drag items, mappings, ...) contained in this content. Used to mint fresh, question-scoped ids for newly added components
     *         via {@code max(componentIds) + 1} and to validate id uniqueness within a question.
     */
    Set<Long> componentIds();

    /**
     * Compares two content values by the JSON they would be persisted as.
     * <p>
     * Implementations need value equality, because Hibernate dirty-checks this JSON-mapped attribute with
     * {@code equals}. Without it the snapshot Hibernate deep-copies on load never equals the current value, every loaded
     * {@link QuizQuestion} counts as modified, and any flush that happens to hold one rewrites the whole
     * {@code quiz_question} row - including from a student's submission request, which must never write a question.
     * <p>
     * The comparison deliberately uses the serialized form rather than delegating to the nested components' own
     * {@code equals}: those extend {@code DomainObject} and compare by id only, so an edited answer option text would
     * look unchanged and the edit would be silently dropped. Two contents that serialize identically are, by
     * construction, identical in the column.
     *
     * @param first  the first content, may be null
     * @param second the second content, may be null
     * @return true if both would be persisted as the same JSON
     */
    static boolean haveEqualPersistedForm(@Nullable QuizQuestionContent first, @Nullable QuizQuestionContent second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.getClass() != second.getClass()) {
            return false;
        }
        String firstJson = persistedForm(first);
        String secondJson = persistedForm(second);
        if (firstJson == null || secondJson == null) {
            // Fall back to identity: reporting "not equal" keeps the previous behaviour (an extra write) rather than
            // risking a dropped change.
            return false;
        }
        return firstJson.equals(secondJson);
    }

    /**
     * @param content the content to render
     * @return the JSON this content would be persisted as, or null if it cannot be rendered
     */
    @Nullable
    static String persistedForm(QuizQuestionContent content) {
        try {
            return COMPARISON_MAPPER.writeValueAsString(content);
        }
        catch (JacksonException e) {
            log.warn("Could not render quiz question content for comparison, treating it as changed", e);
            return null;
        }
    }

    /**
     * @param content the content to hash
     * @return a hash consistent with {@link #haveEqualPersistedForm}
     */
    static int persistedFormHashCode(QuizQuestionContent content) {
        String json = persistedForm(content);
        return json == null ? System.identityHashCode(content) : json.hashCode();
    }
}
