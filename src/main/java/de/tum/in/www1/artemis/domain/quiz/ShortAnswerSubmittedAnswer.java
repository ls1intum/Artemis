package de.tum.in.www1.artemis.domain.quiz;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;
import javax.validation.Valid;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A ShortAnswerSubmittedAnswer.
 */
@Entity
@DiscriminatorValue(value = "SA")
@JsonTypeName("short-answer")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSubmittedAnswer extends SubmittedAnswer {

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "submitted_answer_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    @Valid
    private Set<ShortAnswerSubmittedText> submittedTexts = new HashSet<>();

    public Set<ShortAnswerSubmittedText> getSubmittedTexts() {
        return submittedTexts;
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

    /**
     * Delete all references to question, solutions and spots if the question was changed
     *
     * @param quizExercise the changed quizExercise-object
     */
    @Override
    public void checkAndDeleteReferences(QuizExercise quizExercise) {
        // Delete all references to question, spots and solutions if the question was deleted
        if (!quizExercise.getQuizQuestions().contains(getQuizQuestion())) {
            setQuizQuestion(null);
            submittedTexts = null;
        }
        else {
            // find same quizQuestion in quizExercise
            QuizQuestion quizQuestion = quizExercise.findQuestionById(getQuizQuestion().getId());

            // Check if a solution or spot was deleted and delete the mappings with it
            checkAndDeleteSubmittedTexts((ShortAnswerQuestion) quizQuestion);
        }
    }

    /**
     * Check if a spot were deleted and delete reference to in submittedTexts
     *
     * @param question the changed question with the changed spot
     */
    private void checkAndDeleteSubmittedTexts(ShortAnswerQuestion question) {

        if (question != null) {
            // Check if a solution or spot was deleted and delete reference to it in mappings
            Set<ShortAnswerSubmittedText> selectedSubmittedTextsToDelete = new HashSet<>();
            for (ShortAnswerSubmittedText submittedText : this.getSubmittedTexts()) {
                if ((!question.getSpots().contains(submittedText.getSpot()))) {
                    selectedSubmittedTextsToDelete.add(submittedText);
                }
            }
            for (ShortAnswerSubmittedText submittedTextToDelete : selectedSubmittedTextsToDelete) {
                this.removeSubmittedTexts(submittedTextToDelete);
            }
        }
    }

    /**
     * Gets a ShortAnswerSubmittedText, that correspond to a given spot
     *
     * @param spot the ShortAnswerSpot for which the ShortAnswerSubmittedText should be determined
     * @return the ShortAnswerSubmittedText or null if nothing was found
     */
    public ShortAnswerSubmittedText getSubmittedTextForSpot(ShortAnswerSpot spot) {
        for (ShortAnswerSubmittedText submittedText : this.getSubmittedTexts()) {
            if (submittedText.getSpot().equals(spot)) {
                return submittedText;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "ShortAnswerSubmittedAnswer{" + "id=" + getId() + "}";
    }

    @Override
    public void filterOutCorrectAnswers() {
        super.filterOutCorrectAnswers();
        this.getSubmittedTexts().forEach(submittedText -> submittedText.setIsCorrect(null));
    }
}
