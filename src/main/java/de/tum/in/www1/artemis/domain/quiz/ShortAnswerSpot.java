package de.tum.in.www1.artemis.domain.quiz;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.TempIdObject;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A ShortAnswerSpot.
 */
@Entity
@Table(name = "short_answer_spot")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSpot extends TempIdObject implements QuizQuestionComponent<ShortAnswerQuestion> {

    @Column(name = "spotNr")
    @JsonView(QuizView.Before.class)
    private Integer spotNr;

    @Column(name = "width")
    @JsonView(QuizView.Before.class)
    private Integer width;

    @Column(name = "invalid")
    @JsonView(QuizView.Before.class)
    private Boolean invalid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    @JsonIgnore
    private ShortAnswerQuestion question;

    public Integer getSpotNr() {
        return spotNr;
    }

    public ShortAnswerSpot spotNr(Integer spotNr) {
        this.spotNr = spotNr;
        return this;
    }

    public void setSpotNr(Integer spotNr) {
        this.spotNr = spotNr;
    }

    public Integer getWidth() {
        return width;
    }

    public ShortAnswerSpot width(Integer width) {
        this.width = width;
        return this;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public ShortAnswerQuestion getQuestion() {
        return question;
    }

    public void setQuestion(ShortAnswerQuestion shortAnswerQuestion) {
        this.question = shortAnswerQuestion;
    }

    @Override
    public String toString() {
        return "ShortAnswerSpot{" + "id=" + getId() + ", width=" + getWidth() + ", spotNr=" + getSpotNr() + ", invalid='" + isInvalid() + "'" + "}";
    }
}
