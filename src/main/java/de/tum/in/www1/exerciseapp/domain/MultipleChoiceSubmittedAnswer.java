package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A MultipleChoiceSubmittedAnswer.
 */
@Entity
@DiscriminatorValue(value = "MC")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeName("multiple-choice")
public class MultipleChoiceSubmittedAnswer extends SubmittedAnswer implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToMany(fetch = FetchType.EAGER)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JoinTable(name = "multiple_choice_submitted_answer_selected_options",
        joinColumns = @JoinColumn(name = "multiple_choice_submitted_answers_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "selected_options_id", referencedColumnName = "id"))
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

    /**
     * Check if the given answer option is selected in this submitted answer
     * @param answerOption the answer option to check for
     * @return true if the answer option is selected, false otherwise
     */
    public boolean isSelected(AnswerOption answerOption) {
        // search for this answer option in the selected answer options
        for (AnswerOption selectedOption : getSelectedOptions()) {
            if (selectedOption.getId().longValue() == answerOption.getId().longValue()) {
                // this answer option is selected => we can stop searching
                return true;
            }
        }
        // we didn't find the answer option => it wasn't selected
        return false;
    }

    /**
     * Check if answerOptions were deleted and delete reference to in selectedOptions
     * @param question the changed question with the answerOptions
     */
    public void checkForDeletedAnswerOptions(MultipleChoiceQuestion question) {

        if( question != null) {
            // Check if an answerOption was deleted and delete reference to in selectedOptions
            Set<AnswerOption> selectedOptionsToDelete = new HashSet<>();
            for (AnswerOption answerOption : this.getSelectedOptions()) {
                if (!question.getAnswerOptions().contains(answerOption)) {
                    selectedOptionsToDelete.add(answerOption);
                }
            }
            this.getSelectedOptions().removeAll(selectedOptionsToDelete);
        }
    }

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
