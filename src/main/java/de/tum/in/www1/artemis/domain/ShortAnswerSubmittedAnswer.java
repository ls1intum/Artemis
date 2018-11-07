package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

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

    @Override
    public void checkAndDeleteReferences(QuizExercise quizExercise) {
        //TODO Francisco implement
    }
}
