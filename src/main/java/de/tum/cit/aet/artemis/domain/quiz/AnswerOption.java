package de.tum.cit.aet.artemis.domain.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.view.QuizView;

/**
 * A AnswerOption.
 */
@Entity
@Table(name = "answer_option")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AnswerOption extends DomainObject implements QuizQuestionComponent<MultipleChoiceQuestion> {

    @Column(name = "text")
    @JsonView(QuizView.Before.class)
    private String text;

    @Column(name = "hint")
    @JsonView(QuizView.Before.class)
    private String hint;

    @Column(name = "explanation", length = 500)
    @JsonView(QuizView.After.class)
    private String explanation;

    @Column(name = "is_correct")
    @JsonView(QuizView.After.class)
    private Boolean isCorrect;

    @Column(name = "invalid")
    @JsonView(QuizView.Before.class)
    private Boolean invalid = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private MultipleChoiceQuestion question;

    public String getText() {
        return text;
    }

    public AnswerOption text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getHint() {
        return hint;
    }

    public AnswerOption hint(String hint) {
        this.hint = hint;
        return this;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public String getExplanation() {
        return explanation;
    }

    public AnswerOption explanation(String explanation) {
        this.explanation = explanation;
        return this;
    }

    public AnswerOption isInvalid(boolean invalid) {
        this.invalid = invalid;
        return this;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
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
