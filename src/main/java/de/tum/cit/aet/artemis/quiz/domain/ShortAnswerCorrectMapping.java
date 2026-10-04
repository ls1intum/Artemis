package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * The normalized, persisted form of a correct short-answer mapping: it states that the solution with id {@link #solutionId} is a correct answer for the spot with id
 * {@link #spotId}. Stored inside {@link ShortAnswerQuestionContent#getCorrectMappings()} in the {@code quiz_question.content} JSON column.
 * <p>
 * This is an internal storage type (never serialized to the client directly). The object-based {@link ShortAnswerMapping} is the wire/in-memory representation;
 * {@link ShortAnswerQuestion#getCorrectMappings()} / {@code setCorrectMappings(...)} convert between the two, resolving ids against the question's spots and solutions.
 * <p>
 * It extends {@link DomainObject} to reuse the {@code id} field (the question-scoped mapping id, used by re-evaluation to match mappings across an edit) and its id-based
 * {@code equals}/{@code hashCode}; the inherited JPA annotations are inert because this is a plain JSON-embedded POJO, not an {@code @Entity}. Mirrors
 * {@link DragAndDropCorrectMapping}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerCorrectMapping extends DomainObject {

    @JsonProperty("spotId")
    private Long spotId;

    @JsonProperty("solutionId")
    private Long solutionId;

    @JsonProperty("invalid")
    private Boolean invalid;

    public ShortAnswerCorrectMapping() {
    }

    public ShortAnswerCorrectMapping(Long id, Long spotId, Long solutionId, Boolean invalid) {
        setId(id);
        this.spotId = spotId;
        this.solutionId = solutionId;
        this.invalid = invalid;
    }

    public Long getSpotId() {
        return spotId;
    }

    public void setSpotId(Long spotId) {
        this.spotId = spotId;
    }

    public Long getSolutionId() {
        return solutionId;
    }

    public void setSolutionId(Long solutionId) {
        this.solutionId = solutionId;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }
}
