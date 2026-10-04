package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A ShortAnswerMapping links a {@link ShortAnswerSpot} to a {@link ShortAnswerSolution}.
 * <p>
 * This is the in-memory / wire representation used by the REST DTOs, the raw exam submission/conduction endpoints, the scoring strategies and the client. It carries the spot and
 * solution as nested objects (the shape the client expects), plus derived, transient {@code shortAnswerSpotIndex}/{@code shortAnswerSolutionIndex} for the editor UI.
 * <p>
 * It is intentionally <b>not</b> what gets persisted: a question's correct mappings are stored inside {@link ShortAnswerQuestionContent} as normalized, id-based
 * {@link ShortAnswerCorrectMapping} entries. {@link ShortAnswerQuestion#getCorrectMappings()} builds these object-based mappings on demand by resolving the stored ids against the
 * owning question, and {@code setCorrectMappings(...)} extracts the ids back into the stored form. Formerly a JPA entity backed by {@code short_answer_mapping}; it is now a plain
 * POJO. Mirrors {@link DragAndDropMapping}.
 * <p>
 * It still extends {@link DomainObject} to reuse the {@code id} field and its id-based {@code equals}/{@code hashCode}; the inherited JPA annotations are inert because this class
 * is no longer an {@code @Entity}. For a question's correct mapping the {@code id} is the question-scoped mapping id.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerMapping extends DomainObject {

    // Derived, transient: the position of the spot / solution within the question's ordered lists. Populated when building the mapping from stored content; never persisted.
    private Integer shortAnswerSpotIndex;

    private Integer shortAnswerSolutionIndex;

    private Boolean invalid;

    private ShortAnswerSolution solution;

    private ShortAnswerSpot spot;

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

    @Override
    public String toString() {
        return "ShortAnswerMapping{" + "id=" + getId() + ", shortAnswerSpotIndex=" + getShortAnswerSpotIndex() + ", shortAnswerSolutionIndex=" + getShortAnswerSolutionIndex()
                + ", invalid='" + isInvalid() + "'" + "}";
    }
}
