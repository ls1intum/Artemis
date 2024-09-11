package de.tum.cit.aet.artemis.domain.quiz;

import static de.tum.cit.aet.artemis.config.Constants.MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.view.QuizView;
import me.xdrop.fuzzywuzzy.FuzzySearch;

/**
 * A ShortAnswerSubmittedText.
 */
@Entity
@Table(name = "short_answer_submitted_text")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSubmittedText extends DomainObject {

    @Column(name = "text")
    @Size(max = MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH, message = "The submitted answer text is too long.")
    @JsonView(QuizView.Before.class)
    private String text;

    @Column(name = "is_correct")
    @JsonView(QuizView.Before.class)
    private Boolean isCorrect;

    @OneToOne
    @JoinColumn()
    @JsonView(QuizView.Before.class)
    private ShortAnswerSpot spot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private ShortAnswerSubmittedAnswer submittedAnswer;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Boolean isIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public ShortAnswerSpot getSpot() {
        return spot;
    }

    public void setSpot(ShortAnswerSpot shortAnswerSpot) {
        this.spot = shortAnswerSpot;
    }

    public ShortAnswerSubmittedAnswer getSubmittedAnswer() {
        return submittedAnswer;
    }

    public void setSubmittedAnswer(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
        this.submittedAnswer = shortAnswerSubmittedAnswer;
    }

    /**
     * This function checks if the submittedText (typos included) matches the solution. <a href="https://github.com/xdrop/fuzzywuzzy">...</a>
     *
     * @param submittedText for a short answer question
     * @param solution      of the short answer question
     * @return boolean true if submittedText fits the restrictions above, false when not
     */
    public boolean isSubmittedTextCorrect(String submittedText, String solution) {
        if (Objects.equals(submittedText, solution)) {
            // when both values are identical, we can return early
            return true;
        }
        if (submittedText == null) {
            // prevent null pointer exceptions
            return false;
        }
        ShortAnswerQuestion saQuestion = (ShortAnswerQuestion) submittedAnswer.getQuizQuestion();
        int similarityValue = Objects.requireNonNullElse(saQuestion.getSimilarityValue(), 85); // default value
        if (Boolean.TRUE.equals(saQuestion.matchLetterCase())) {
            // only trim whitespace left and right
            return FuzzySearch.ratio(submittedText.trim(), solution.trim()) >= similarityValue;
        }
        // also use lowercase to allow different cases in the submitted text
        return FuzzySearch.ratio(submittedText.toLowerCase().trim(), solution.toLowerCase().trim()) >= similarityValue;
    }

    @Override
    public String toString() {
        return "ShortAnswerSubmittedText{" + "id=" + getId() + ", text='" + getText() + "'" + ", isCorrect='" + isIsCorrect() + "'" + "}";
    }
}
