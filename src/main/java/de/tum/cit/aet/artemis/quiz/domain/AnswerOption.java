package de.tum.cit.aet.artemis.quiz.domain;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An answer option owned by a {@link MultipleChoiceQuestion}.
 *
 * The explicit JSON property names form the persistence contract of the answer_options column. Keep them stable when
 * renaming Java accessors.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AnswerOption {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("text")
    private String text;

    @JsonProperty("hint")
    private String hint;

    @JsonProperty("explanation")
    private String explanation;

    @JsonProperty("isCorrect")
    private Boolean isCorrect;

    @JsonProperty("invalid")
    private boolean invalid;

    public AnswerOption() {
    }

    public AnswerOption(Long id, String text, String hint, String explanation, Boolean isCorrect, boolean invalid) {
        this.id = id;
        this.text = text;
        this.hint = hint;
        this.explanation = explanation;
        this.isCorrect = isCorrect;
        this.invalid = invalid;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public AnswerOption text(String text) {
        this.text = text;
        return this;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public AnswerOption hint(String hint) {
        this.hint = hint;
        return this;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public AnswerOption explanation(String explanation) {
        this.explanation = explanation;
        return this;
    }

    public AnswerOption isInvalid(boolean invalid) {
        this.invalid = invalid;
        return this;
    }

    public Boolean isIsCorrect() {
        return isCorrect;
    }

    public AnswerOption isCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
        return this;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    @Override
    public String toString() {
        return "AnswerOption{" + "id=" + getId() + ", text='" + getText() + "'" + ", hint='" + getHint() + "'" + ", explanation='" + getExplanation() + "'" + ", isCorrect='"
                + isIsCorrect() + "'" + ", invalid='" + isInvalid() + "'" + "}";
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AnswerOption answerOption)) {
            return false;
        }
        if (id == null || answerOption.id == null) {
            return false;
        }
        if (!Objects.equals(id, answerOption.id) || !Objects.equals(text, answerOption.text) || !Objects.equals(hint, answerOption.hint)
                || !Objects.equals(invalid, answerOption.invalid)) {
            return false;
        }
        // Student/exam responses intentionally hide solution fields. Treat this projection as the same option while
        // still comparing solution fields for valid persisted options so Hibernate detects JSON content changes.
        if (hasHiddenSolutionFields() || answerOption.hasHiddenSolutionFields()) {
            return true;
        }
        return Objects.equals(explanation, answerOption.explanation) && Objects.equals(isCorrect, answerOption.isCorrect);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, text, hint, invalid);
    }

    private boolean hasHiddenSolutionFields() {
        return isCorrect == null && explanation == null;
    }
}
