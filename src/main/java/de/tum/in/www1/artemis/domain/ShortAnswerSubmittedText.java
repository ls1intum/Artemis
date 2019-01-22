package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

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

    @OneToOne
    @JoinColumn(unique = true)
    @JsonView(QuizView.Before.class)
    private ShortAnswerSpot spot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private ShortAnswerSubmittedAnswer submittedAnswer;


    //FDE added afterwards
    @Column(name = "is_correct")
    @JsonView(QuizView.After.class)
    private Boolean isCorrect;

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

    public ShortAnswerSubmittedText text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public ShortAnswerSpot getSpot() {
        return spot;
    }

    public ShortAnswerSubmittedText spot(ShortAnswerSpot shortAnswerSpot) {
        this.spot = shortAnswerSpot;
        return this;
    }

    public void setSpot(ShortAnswerSpot shortAnswerSpot) {
        this.spot = shortAnswerSpot;
    }

    public ShortAnswerSubmittedAnswer getSubmittedAnswer() {
        return submittedAnswer;
    }

    public ShortAnswerSubmittedText submittedAnswer(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
        this.submittedAnswer = shortAnswerSubmittedAnswer;
        return this;
    }

    public void setSubmittedAnswer(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
        this.submittedAnswer = shortAnswerSubmittedAnswer;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove



    //FDE added afterwards
    public Boolean isIsCorrect() {
        return isCorrect;
    }

    public ShortAnswerSubmittedText isCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
        return this;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    /*public boolean moreThanOnceInSubmittedAnswer(ShortAnswerSubmittedText submittedText){
        int numberOfSubmittedText = 0;
        for(ShortAnswerSubmittedText submittedTextFromSubmittedAnswer : this.submittedAnswer.getSubmittedTexts()){
            if(submittedTextFromSubmittedAnswer.getText().equals(submittedText.getText())){
                numberOfSubmittedText++;
            }
        }
        if(numberOfSubmittedText == 1){
            return false;
        } else {
            return true;
        }
    }*/

    //TODO FDE: check if text input is correct needs to improve
    public boolean isSubmittedTextCorrect(String submittedText, String solution){
        return submittedText.equalsIgnoreCase(solution);
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
        return "ShortAnswerSubmittedText{" +
            "id=" + getId() +
            ", text='" + getText() + "'" +
           ", isCorrect='" + isIsCorrect() + "'"+
            "}";
    }
}
