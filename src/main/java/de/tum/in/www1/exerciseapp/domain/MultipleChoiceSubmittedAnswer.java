package de.tum.in.www1.exerciseapp.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A MultipleChoiceSubmittedAnswer.
 */
@Entity
@DiscriminatorValue(value="MC")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class MultipleChoiceSubmittedAnswer extends SubmittedAnswer implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToMany
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JoinTable(name = "multiple_choice_submitted_answer_selected_options",
               joinColumns = @JoinColumn(name="multiple_choice_submitted_answers_id", referencedColumnName="id"),
               inverseJoinColumns = @JoinColumn(name="selected_options_id", referencedColumnName="id"))
    private Set<AnswerOption> selectedOptions = new HashSet<>();

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove

    public Set<AnswerOption> getSelectedOptions() {
        return selectedOptions;
    }

    public MultipleChoiceSubmittedAnswer selectedOptions(Set<AnswerOption> answerOptions) {
        this.selectedOptions = answerOptions;
        return this;
    }

    public MultipleChoiceSubmittedAnswer addSelectedOptions(AnswerOption answerOption) {
        this.selectedOptions.add(answerOption);
        return this;
    }

    public MultipleChoiceSubmittedAnswer removeSelectedOptions(AnswerOption answerOption) {
        this.selectedOptions.remove(answerOption);
        return this;
    }

    public void setSelectedOptions(Set<AnswerOption> answerOptions) {
        this.selectedOptions = answerOptions;
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
        MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer = (MultipleChoiceSubmittedAnswer) o;
        if (multipleChoiceSubmittedAnswer.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), multipleChoiceSubmittedAnswer.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "MultipleChoiceSubmittedAnswer{" +
            "id=" + getId() +
            "}";
    }
}
