package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A AnswerOption.
 */
@Entity
@Table(name = "answer_option")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AnswerOption implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(QuizView.Before.class)
    private Long id;

    @Column(name = "text")
    @JsonView(QuizView.Before.class)
    private String text;

    @Column(name = "hint")
    @JsonView(QuizView.Before.class)
    private String hint;

    @Column(name = "explanation")
    @JsonView(QuizView.After.class)
    private String explanation;

    @Column(name = "is_correct")
    @JsonView(QuizView.After.class)
    private Boolean isCorrect;

    @Column(name = "invalid")
    @JsonView(QuizView.Before.class)
    private Boolean invalid = false;

    @ManyToOne
    @JsonIgnore
    private MultipleChoiceQuestion question;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
        return invalid == null ? false : invalid;
    }

    public AnswerOption invalid(Boolean invalid) {
        this.invalid = invalid;
        return this;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public MultipleChoiceQuestion getQuestion() {
        return question;
    }

    public AnswerOption question(MultipleChoiceQuestion multipleChoiceQuestion) {
        this.question = multipleChoiceQuestion;
        return this;
    }

    public void setQuestion(MultipleChoiceQuestion multipleChoiceQuestion) {
        this.question = multipleChoiceQuestion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnswerOption answerOption = (AnswerOption) o;
        if (answerOption.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), answerOption.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "AnswerOption{" +
            "id=" + getId() +
            ", text='" + getText() + "'" +
            ", hint='" + getHint() + "'" +
            ", explanation='" + getExplanation() + "'" +
            ", isCorrect='" + isIsCorrect() + "'" +
            ", invalid='" + isInvalid() + "'" +
            "}";
    }
}
