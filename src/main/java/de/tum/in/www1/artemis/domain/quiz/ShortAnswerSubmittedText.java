package de.tum.in.www1.artemis.domain.quiz;

import javax.persistence.*;

import me.xdrop.fuzzywuzzy.FuzzySearch;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A ShortAnswerSubmittedText.
 */
@Entity
@Table(name = "short_answer_submitted_text")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSubmittedText extends DomainObject {

    @Column(name = "text")
    @JsonView(QuizView.Before.class)
    private String text;

    @Column(name = "isCorrect")
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
     * This function checks if the submittedText (typos included) matches the solution. https://github.com/xdrop/fuzzywuzzy
     *
     * @param submittedText for a short answer question
     * @param solution of the short answer question
     * @return boolean true if submittedText fits the restrictions above, false when not
     */
    public boolean isSubmittedTextCorrect(String submittedText, String solution) {
        ShortAnswerQuestion saQuestion = submittedAnswer != null ? (ShortAnswerQuestion) submittedAnswer.getQuizQuestion() : new ShortAnswerQuestion();
        boolean matchLetterCase = saQuestion.matchLetterCase() != null && saQuestion.matchLetterCase();
        int similarityValue = saQuestion.getSimilarityValue() != null ? saQuestion.getSimilarityValue() : 85;
        if (matchLetterCase) {
            return FuzzySearch.ratio(submittedText.trim(), solution.trim()) >= similarityValue;
        }
        return FuzzySearch.ratio(submittedText.toLowerCase().trim(), solution.toLowerCase().trim()) >= similarityValue;
    }

    @Override
    public String toString() {
        return "ShortAnswerSubmittedText{" + "id=" + getId() + ", text='" + getText() + "'" + ", isCorrect='" + isIsCorrect() + "'" + "}";
    }
}
