package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A MultipleChoiceQuestion.
 */
@Entity
@DiscriminatorValue(value="MC")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeName("multiple-choice")
public class MultipleChoiceQuestion extends Question implements Serializable {

    private static final long serialVersionUID = 1L;

    @OneToMany(mappedBy = "question")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<AnswerOption> answerOptions = new HashSet<>();

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove

    public Set<AnswerOption> getAnswerOptions() {
        return answerOptions;
    }

    public MultipleChoiceQuestion answerOptions(Set<AnswerOption> answerOptions) {
        this.answerOptions = answerOptions;
        return this;
    }

    public MultipleChoiceQuestion addAnswerOptions(AnswerOption answerOption) {
        this.answerOptions.add(answerOption);
        answerOption.setQuestion(this);
        return this;
    }

    public MultipleChoiceQuestion removeAnswerOptions(AnswerOption answerOption) {
        this.answerOptions.remove(answerOption);
        answerOption.setQuestion(null);
        return this;
    }

    public void setAnswerOptions(Set<AnswerOption> answerOptions) {
        this.answerOptions = answerOptions;
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
        MultipleChoiceQuestion multipleChoiceQuestion = (MultipleChoiceQuestion) o;
        if (multipleChoiceQuestion.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), multipleChoiceQuestion.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "MultipleChoiceQuestion{" +
            "id=" + getId() +
            "}";
    }
}
