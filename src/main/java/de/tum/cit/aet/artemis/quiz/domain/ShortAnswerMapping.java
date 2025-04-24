package de.tum.cit.aet.artemis.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A ShortAnswerMapping.
 */
@Entity
@Table(name = "short_answer_mapping")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerMapping extends DomainObject implements QuizQuestionComponent<ShortAnswerQuestion> {

    @Column(name = "short_answer_spot_index")
    private Integer shortAnswerSpotIndex;

    @Column(name = "short_answer_solution_index")
    private Integer shortAnswerSolutionIndex;

    @Column(name = "invalid")
    private Boolean invalid;

    @ManyToOne
    private ShortAnswerSolution solution;

    @ManyToOne
    private ShortAnswerSpot spot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private ShortAnswerQuestion question;

    public Integer getShortAnswerSpotIndex() {
        return shortAnswerSpotIndex;
    }

    public void setShortAnswerSpotIndex(Integer shortAnswerSpotIndex) {
        this.shortAnswerSpotIndex = shortAnswerSpotIndex;
    }

    public Integer getShortAnswerSolutionIndex() {
        return shortAnswerSolutionIndex;
    }

    public void setShortAnswerSolutionIndex(Integer shortAnswerSolutionIndex) {
        this.shortAnswerSolutionIndex = shortAnswerSolutionIndex;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public ShortAnswerSolution getSolution() {
        return solution;
    }

    public void setSolution(ShortAnswerSolution shortAnswerSolution) {
        this.solution = shortAnswerSolution;
    }

    public ShortAnswerMapping solution(ShortAnswerSolution shortAnswerSolution) {
        this.solution = shortAnswerSolution;
        return this;
    }

    public ShortAnswerSpot getSpot() {
        return spot;
    }

    public void setSpot(ShortAnswerSpot shortAnswerSpot) {
        this.spot = shortAnswerSpot;
    }

    public ShortAnswerMapping spot(ShortAnswerSpot shortAnswerSpot) {
        this.spot = shortAnswerSpot;
        return this;
    }

    public ShortAnswerQuestion getQuestion() {
        return question;
    }

    @Override
    public void setQuestion(ShortAnswerQuestion shortAnswerQuestion) {
        this.question = shortAnswerQuestion;
    }

    @Override
    public String toString() {
        return "ShortAnswerMapping{" + "id=" + getId() + ", shortAnswerSpotIndex=" + getShortAnswerSpotIndex() + ", shortAnswerSolutionIndex=" + getShortAnswerSolutionIndex()
                + ", invalid='" + isInvalid() + "'" + "}";
    }

}
