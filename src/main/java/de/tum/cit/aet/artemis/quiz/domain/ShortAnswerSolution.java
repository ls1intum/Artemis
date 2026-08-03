package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A ShortAnswerSolution of a {@link ShortAnswerQuestion}.
 * <p>
 * Formerly a JPA entity backed by the {@code short_answer_solution} table; it is now a plain POJO stored inside the question's {@code content} JSON column (see
 * {@link ShortAnswerQuestionContent}). It no longer holds a back-reference to the question or to its mappings. Mirrors {@link DragItem}.
 * <p>
 * It still extends {@link DomainObject} to reuse the {@code id} field and its id-based {@code equals}/{@code hashCode}; the inherited JPA annotations are inert because this class
 * is no longer an {@code @Entity}. The {@code id} is question-scoped (unique within the owning question, not globally).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSolution extends DomainObject {

    private String text;

    private Boolean invalid = false;

    public String getText() {
        return text;
    }

    public ShortAnswerSolution text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    @Override
    public String toString() {
        return "ShortAnswerSolution{" + "id=" + getId() + ", text='" + getText() + "'" + ", invalid='" + isInvalid() + "'" + "}";
    }

}
