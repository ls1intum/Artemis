package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The "correct answer" content of a {@link MultipleChoiceQuestion}: its answer options. Stored inside the {@code quiz_question.content} JSON column.
 * <p>
 * This is a plain POJO (not a JPA entity). It is an internal storage representation and is never serialized directly to the client: {@link MultipleChoiceQuestion} keeps its
 * existing {@code getAnswerOptions()} getter which delegates here, preserving the REST/websocket wire format. Mirrors {@link DragAndDropQuestionContent}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class MultipleChoiceQuestionContent implements QuizQuestionContent {

    @JsonProperty("answerOptions")
    private List<AnswerOption> answerOptions = new ArrayList<>();

    public List<AnswerOption> getAnswerOptions() {
        return answerOptions;
    }

    public void setAnswerOptions(List<AnswerOption> answerOptions) {
        this.answerOptions = answerOptions != null ? answerOptions : new ArrayList<>();
    }

    @Override
    public Set<Long> componentIds() {
        Set<Long> ids = new HashSet<>();
        for (AnswerOption answerOption : answerOptions) {
            if (answerOption.getId() != null) {
                ids.add(answerOption.getId());
            }
        }
        return ids;
    }

    /**
     * Value equality by persisted JSON. See {@link QuizQuestionContent#haveEqualPersistedForm} for why this is needed
     * and why it is not delegated to the nested components.
     *
     * @param other the object to compare with
     * @return true if both would be persisted as the same JSON
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof MultipleChoiceQuestionContent && QuizQuestionContent.haveEqualPersistedForm(this, (MultipleChoiceQuestionContent) other);
    }

    @Override
    public int hashCode() {
        return QuizQuestionContent.persistedFormHashCode(this);
    }
}
