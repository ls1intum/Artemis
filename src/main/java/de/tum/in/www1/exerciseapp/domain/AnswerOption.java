package de.tum.in.www1.exerciseapp.domain;

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
public class AnswerOption implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text")
    private String text;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "correct_score")
    private Integer correctScore;

    @Column(name = "incorrect_score")
    private Integer incorrectScore;

    @ManyToOne
    private MultipleChoiceQuestion question;

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove
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

    public Integer getCorrectScore() {
        return correctScore;
    }

    public AnswerOption correctScore(Integer correctScore) {
        this.correctScore = correctScore;
        return this;
    }

    public void setCorrectScore(Integer correctScore) {
        this.correctScore = correctScore;
    }

    public Integer getIncorrectScore() {
        return incorrectScore;
    }

    public AnswerOption incorrectScore(Integer incorrectScore) {
        this.incorrectScore = incorrectScore;
        return this;
    }

    public void setIncorrectScore(Integer incorrectScore) {
        this.incorrectScore = incorrectScore;
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
    // jhipster-needle-entity-add-getters-setters - Jhipster will add getters and setters here, do not remove

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
            ", isCorrect='" + isIsCorrect() + "'" +
            ", correctScore='" + getCorrectScore() + "'" +
            ", incorrectScore='" + getIncorrectScore() + "'" +
            "}";
    }
}
