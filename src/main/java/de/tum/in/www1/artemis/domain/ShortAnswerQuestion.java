package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.*;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.*;

/**
 * A ShortAnswerQuestion.
 */
@Entity
@DiscriminatorValue(value = "SA")
@JsonTypeName("short-answer")
public class ShortAnswerQuestion extends Question implements Serializable {

    private static final long serialVersionUID = 1L;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderColumn
    @JoinColumn(name = "question_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<ShortAnswerSpot> spots = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderColumn
    @JoinColumn(name = "question_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<ShortAnswerSolution> solutions = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderColumn
    @JoinColumn(name = "question_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.After.class)
    private List<ShortAnswerMapping> correctMappings = new ArrayList<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

    public List<ShortAnswerSpot> getSpots() {
        return spots;
    }

    public ShortAnswerQuestion spots(List<ShortAnswerSpot> shortAnswerSpots) {
        this.spots = shortAnswerSpots;
        return this;
    }

    public ShortAnswerQuestion addSpots(ShortAnswerSpot shortAnswerSpot) {
        this.spots.add(shortAnswerSpot);
        shortAnswerSpot.setQuestion(this);
        return this;
    }

    public ShortAnswerQuestion removeSpots(ShortAnswerSpot shortAnswerSpot) {
        this.spots.remove(shortAnswerSpot);
        shortAnswerSpot.setQuestion(null);
        return this;
    }

    public void setSpots(List<ShortAnswerSpot> shortAnswerSpots) {
        this.spots = shortAnswerSpots;
    }

    public List<ShortAnswerSolution> getSolutions() {
        return solutions;
    }

    public ShortAnswerQuestion solutions(List<ShortAnswerSolution> shortAnswerSolutions) {
        this.solutions = shortAnswerSolutions;
        return this;
    }

    public ShortAnswerQuestion addSolutions(ShortAnswerSolution shortAnswerSolution) {
        this.solutions.add(shortAnswerSolution);
        shortAnswerSolution.setQuestion(this);
        return this;
    }

    public ShortAnswerQuestion removeSolutions(ShortAnswerSolution shortAnswerSolution) {
        this.solutions.remove(shortAnswerSolution);
        shortAnswerSolution.setQuestion(null);
        return this;
    }

    public void setSolutions(List<ShortAnswerSolution> shortAnswerSolutions) {
        this.solutions = shortAnswerSolutions;
    }

    public List<ShortAnswerMapping> getCorrectMappings() {
        return correctMappings;
    }

    public ShortAnswerQuestion correctMappings(List<ShortAnswerMapping> shortAnswerMappings) {
        this.correctMappings = shortAnswerMappings;
        return this;
    }

    public ShortAnswerQuestion addCorrectMappings(ShortAnswerMapping shortAnswerMapping) {
        this.correctMappings.add(shortAnswerMapping);
        shortAnswerMapping.setQuestion(this);
        return this;
    }

    public ShortAnswerQuestion removeCorrectMappings(ShortAnswerMapping shortAnswerMapping) {
        this.correctMappings.remove(shortAnswerMapping);
        shortAnswerMapping.setQuestion(null);
        return this;
    }

    public void setCorrectMappings(List<ShortAnswerMapping> shortAnswerMappings) {
        this.correctMappings = shortAnswerMappings;
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
        ShortAnswerQuestion shortAnswerQuestion = (ShortAnswerQuestion) o;
        if (shortAnswerQuestion.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), shortAnswerQuestion.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ShortAnswerQuestion{" +
            "id=" + getId() +
            "}";
    }

    @Override
    public void undoUnallowedChanges(Question originalQuestion) {
        //TODO Francisco implement
    }

    @Override
    public boolean isUpdateOfResultsAndStatisticsNecessary(Question originalQuestion) {
        //TODO Francisco implement
        return false;
    }


    //TODO Francisco Have a look at DndQuestion.java and MCQuestion.java and implement the required methods, constructors and annotations

}
