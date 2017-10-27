package de.tum.in.www1.exerciseapp.domain;

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

    @Column(name = "counter")
    private Integer counter;

    @ManyToOne
    private MultipleChoiceStatistic multipleChoiceStatistic;

    @OneToOne
    @JoinColumn(unique = true)
    private AnswerOption answer;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getCounter() {
        return counter;
    }

    public AnswerCounter counter(Integer counter) {
        this.counter = counter;
        return this;
    }

    public void setCounter(Integer counter) {
        this.counter = counter;
    }


    public MultipleChoiceStatistic getMultipleChoiceStatistic() {
        return multipleChoiceStatistic;
    }

    public AnswerCounter multipleChoiceStatistic(MultipleChoiceStatistic multipleChoiceStatistic) {
        this.multipleChoiceStatistic = multipleChoiceStatistic;
        return this;
    }

    public void setMultipleChoiceStatistic(MultipleChoiceStatistic multipleChoiceStatistic) {
        this.multipleChoiceStatistic = multipleChoiceStatistic;
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
            ", counter='" + getCounter() + "'" +
            "}";
    }
}
