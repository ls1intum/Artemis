package de.tum.cit.aet.artemis.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A AnswerOption.
 */
@Entity
@Table(name = "answer_option")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AnswerOption extends DomainObject implements QuizQuestionComponent<MultipleChoiceQuestion> {

    @Column(name = "text")
    private String text;

    @Column(name = "hint")
    private String hint;

    @Column(name = "explanation", length = 500)
    private String explanation;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "invalid")
    private Boolean invalid = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private MultipleChoiceQuestion question;

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

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public MultipleChoiceQuestion getQuestion() {
        return question;
    }

    @Override
    public void setQuestion(MultipleChoiceQuestion multipleChoiceQuestion) {
        this.question = multipleChoiceQuestion;
    }

    @Override
    public String toString() {
        return "AnswerOption{" + "id=" + getId() + ", text='" + getText() + "'" + ", hint='" + getHint() + "'" + ", explanation='" + getExplanation() + "'" + ", isCorrect='"
                + isIsCorrect() + "'" + ", invalid='" + isInvalid() + "'" + "}";
    }

}
