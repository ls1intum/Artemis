package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;

import me.xdrop.fuzzywuzzy.FuzzySearch;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A ShortAnswerSubmittedText.
 */
@Entity
@Table(name = "short_answer_submitted_text")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ShortAnswerSubmittedText implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(QuizView.Before.class)
    private Long id;

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

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
        return FuzzySearch.ratio(submittedText.toLowerCase().trim(), solution.toLowerCase().trim()) > 85;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ShortAnswerSubmittedText shortAnswerSubmittedText = (ShortAnswerSubmittedText) o;
        if (shortAnswerSubmittedText.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), shortAnswerSubmittedText.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ShortAnswerSubmittedText{" + "id=" + getId() + ", text='" + getText() + "'" + ", isCorrect='" + isIsCorrect() + "'" + "}";
    }
}
