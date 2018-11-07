package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * A ShortAnswerMapping.
 */
@Entity
@Table(name = "short_answer_mapping")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ShortAnswerMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(QuizView.Before.class)
    private Long id;

    @Column(name = "short_answer_spot_index")
    @JsonView(QuizView.Before.class)
    private Integer shortAnswerSpotIndex;

    @Column(name = "short_answer_solution_index")
    @JsonView(QuizView.Before.class)
    private Integer shortAnswerSolutionIndex;

    @Column(name = "invalid")
    @JsonView(QuizView.Before.class)
    private Boolean invalid;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private ShortAnswerSolution solution;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private ShortAnswerSpot spot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private ShortAnswerQuestion question;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getShortAnswerSpotIndex() {
        return shortAnswerSpotIndex;
    }

    public ShortAnswerMapping shortAnswerSpotIndex(Integer shortAnswerSpotIndex) {
        this.shortAnswerSpotIndex = shortAnswerSpotIndex;
        return this;
    }

    public void setShortAnswerSpotIndex(Integer shortAnswerSpotIndex) {
        this.shortAnswerSpotIndex = shortAnswerSpotIndex;
    }

    public Integer getShortAnswerSolutionIndex() {
        return shortAnswerSolutionIndex;
    }

    public ShortAnswerMapping shortAnswerSolutionIndex(Integer shortAnswerSolutionIndex) {
        this.shortAnswerSolutionIndex = shortAnswerSolutionIndex;
        return this;
    }

    public void setShortAnswerSolutionIndex(Integer shortAnswerSolutionIndex) {
        this.shortAnswerSolutionIndex = shortAnswerSolutionIndex;
    }

    public Boolean isInvalid() {
        return invalid;
    }

    public ShortAnswerMapping invalid(Boolean invalid) {
        this.invalid = invalid;
        return this;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public ShortAnswerSolution getSolution() {
        return solution;
    }

    public ShortAnswerMapping solution(ShortAnswerSolution shortAnswerSolution) {
        this.solution = shortAnswerSolution;
        return this;
    }

    public void setSolution(ShortAnswerSolution shortAnswerSolution) {
        this.solution = shortAnswerSolution;
    }

    public ShortAnswerSpot getSpot() {
        return spot;
    }

    public ShortAnswerMapping spot(ShortAnswerSpot shortAnswerSpot) {
        this.spot = shortAnswerSpot;
        return this;
    }

    public void setSpot(ShortAnswerSpot shortAnswerSpot) {
        this.spot = shortAnswerSpot;
    }

    public ShortAnswerQuestion getQuestion() {
        return question;
    }

    public ShortAnswerMapping question(ShortAnswerQuestion shortAnswerQuestion) {
        this.question = shortAnswerQuestion;
        return this;
    }

    public void setQuestion(ShortAnswerQuestion shortAnswerQuestion) {
        this.question = shortAnswerQuestion;
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
        ShortAnswerMapping shortAnswerMapping = (ShortAnswerMapping) o;
        if (shortAnswerMapping.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), shortAnswerMapping.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ShortAnswerMapping{" +
            "id=" + getId() +
            ", shortAnswerSpotIndex=" + getShortAnswerSpotIndex() +
            ", shortAnswerSolutionIndex=" + getShortAnswerSolutionIndex() +
            ", invalid='" + isInvalid() + "'" +
            "}";
    }
}
