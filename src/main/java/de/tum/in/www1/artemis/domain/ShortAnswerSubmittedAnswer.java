package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A ShortAnswerSubmittedAnswer.
 */
@Entity
@DiscriminatorValue(value="SA")
@JsonTypeName("short-answer")
public class ShortAnswerSubmittedAnswer extends SubmittedAnswer implements Serializable {

    private static final long serialVersionUID = 1L;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "submitted_answer_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private Set<ShortAnswerSubmittedText> submittedTexts = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

    public Set<ShortAnswerSubmittedText> getSubmittedTexts() {
        return submittedTexts;
    }

    public ShortAnswerSubmittedAnswer submittedTexts(Set<ShortAnswerSubmittedText> shortAnswerSubmittedTexts) {
        this.submittedTexts = shortAnswerSubmittedTexts;
        return this;
    }

    public ShortAnswerSubmittedAnswer addSubmittedTexts(ShortAnswerSubmittedText shortAnswerSubmittedText) {
        this.submittedTexts.add(shortAnswerSubmittedText);
        shortAnswerSubmittedText.setSubmittedAnswer(this);
        return this;
    }

    public ShortAnswerSubmittedAnswer removeSubmittedTexts(ShortAnswerSubmittedText shortAnswerSubmittedText) {
        this.submittedTexts.remove(shortAnswerSubmittedText);
        shortAnswerSubmittedText.setSubmittedAnswer(null);
        return this;
    }

    public void setSubmittedTexts(Set<ShortAnswerSubmittedText> shortAnswerSubmittedTexts) {
        this.submittedTexts = shortAnswerSubmittedTexts;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    /**
     * Delete all references to question, solutions and spots if the question was changed
     *
     * @param quizExercise the changed quizExercise-object
     */
    @Override
    public void checkAndDeleteReferences(QuizExercise quizExercise) {
        // Delete all references to question, spots and solutions if the question was deleted
        if (!quizExercise.getQuestions().contains(getQuestion())) {
            setQuestion(null);
            submittedTexts = null;
        } else {
            // find same question in quizExercise
            Question question = quizExercise.findQuestionById(getQuestion().getId());

            // Check if a solution or spot was deleted and delete the mappings with it
            checkAndDeleteSubmittedTexts((ShortAnswerQuestion) question);
        }
    }

    /**
     * Check if a  spot were deleted and delete reference to in submittedTexts
     * @param question the changed question with the changed  spot
     */
    private void checkAndDeleteSubmittedTexts(ShortAnswerQuestion question) {

        if( question != null) {
            // Check if a solution or spot was deleted and delete reference to it in mappings
            Set<ShortAnswerSubmittedText> selectedSubmittedTextsToDelete = new HashSet<>();
            for (ShortAnswerSubmittedText submittedText : this.getSubmittedTexts()) {
                if ((!question.getSpots().contains(submittedText.getSpot()))) {
                    selectedSubmittedTextsToDelete.add(submittedText);
                }
            }
            for (ShortAnswerSubmittedText submittedTextToDelete: selectedSubmittedTextsToDelete) {
                this.removeSubmittedTexts(submittedTextToDelete);
            }
        }
    }

    public boolean submittedTextMoreThanOnceInSubmittedAnswer(ShortAnswerSubmittedText submittedText){
        int numberOfSubmittedText = 0;
        for(ShortAnswerSubmittedText submittedTextFromSubmittedAnswer : this.getSubmittedTexts()){
            if(submittedTextFromSubmittedAnswer.getText().equalsIgnoreCase(submittedText.getText())){
                numberOfSubmittedText++;
            }
        }
        if(numberOfSubmittedText == 1){
            return false;
        } else {
            return true;
        }
    }

    public ShortAnswerSubmittedText getSubmittedTextForSpot(ShortAnswerSpot spot) {
        for(ShortAnswerSubmittedText submittedText : this.getSubmittedTexts()) {
            if(submittedText.getSpot().equals(spot)) {
                return submittedText;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer = (ShortAnswerSubmittedAnswer) o;
        if (shortAnswerSubmittedAnswer.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), shortAnswerSubmittedAnswer.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ShortAnswerSubmittedAnswer{" +
            "id=" + getId() +
            "}";
    }
}
