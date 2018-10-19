package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * A AnswerCounter.
 */
@Entity
@Table(name = "answer_counter")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class AnswerCounter implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne    @JoinColumn(unique = true)
    private AnswerOption answer;

    @ManyToOne
    @JsonIgnoreProperties("answerCounters")
    private MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AnswerOption getAnswer() {
        return answer;
    }

    public AnswerCounter answer(AnswerOption answerOption) {
        this.answer = answerOption;
        return this;
    }

    public void setAnswer(AnswerOption answerOption) {
        this.answer = answerOption;
    }

    public MultipleChoiceQuestionStatistic getMultipleChoiceQuestionStatistic() {
        return multipleChoiceQuestionStatistic;
    }

    public AnswerCounter multipleChoiceQuestionStatistic(MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic) {
        this.multipleChoiceQuestionStatistic = multipleChoiceQuestionStatistic;
        return this;
    }

    public void setMultipleChoiceQuestionStatistic(MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic) {
        this.multipleChoiceQuestionStatistic = multipleChoiceQuestionStatistic;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnswerCounter answerCounter = (AnswerCounter) o;
        if (answerCounter.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), answerCounter.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "AnswerCounter{" +
            "id=" + getId() +
            "}";
    }
}
