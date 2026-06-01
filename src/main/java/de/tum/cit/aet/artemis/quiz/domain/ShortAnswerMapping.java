package de.tum.cit.aet.artemis.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A ShortAnswerMapping.
 */
// No @Cache here on purpose: part of the quiz-submission merge graph. See #12574 / #12584.
@Entity
@Table(name = "short_answer_mapping")
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
    @JoinColumn(name = "question_id")
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

    /**
     * Stable, constant hashCode that does not change when Hibernate assigns the id on persist. This is required because
     * {@link ShortAnswerQuestion#correctMappings} is a {@code Set<ShortAnswerMapping>}: factories and DTO mappers add
     * transient mappings (id == null) to the set, and the id-based default would change after persist and silently
     * break HashSet membership. Returning a constant forces all instances into the same bucket; the (id-based)
     * {@code equals} contract still distinguishes them. The performance impact is negligible — a question's mapping
     * set is small. Mirrors the same pattern on {@link de.tum.cit.aet.artemis.assessment.domain.Feedback}.
     */
    @Override
    public int hashCode() {
        return ShortAnswerMapping.class.hashCode();
    }
}
