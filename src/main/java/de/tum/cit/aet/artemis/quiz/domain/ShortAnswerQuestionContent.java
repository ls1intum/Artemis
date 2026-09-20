package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The "correct answer" content of a {@link ShortAnswerQuestion}: its spots, solutions and correct mappings. Stored inside the {@code quiz_question.content} JSON column.
 * <p>
 * This is a plain POJO (not a JPA entity). It is an internal storage representation and is never serialized directly to the client: {@link ShortAnswerQuestion} keeps its existing
 * getters ({@code getSpots()} / {@code getSolutions()} / {@code getCorrectMappings()}) which delegate here, preserving the REST/websocket wire format. Correct mappings are stored
 * normalized (id-based, see {@link ShortAnswerCorrectMapping}) and resolved to object-based {@link ShortAnswerMapping}s on access. Mirrors {@link DragAndDropQuestionContent}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class ShortAnswerQuestionContent implements QuizQuestionContent {

    @JsonProperty("spots")
    private List<ShortAnswerSpot> spots = new ArrayList<>();

    @JsonProperty("solutions")
    private List<ShortAnswerSolution> solutions = new ArrayList<>();

    @JsonProperty("correctMappings")
    private List<ShortAnswerCorrectMapping> correctMappings = new ArrayList<>();

    public List<ShortAnswerSpot> getSpots() {
        return spots;
    }

    public void setSpots(List<ShortAnswerSpot> spots) {
        this.spots = spots != null ? spots : new ArrayList<>();
    }

    public List<ShortAnswerSolution> getSolutions() {
        return solutions;
    }

    public void setSolutions(List<ShortAnswerSolution> solutions) {
        this.solutions = solutions != null ? solutions : new ArrayList<>();
    }

    public List<ShortAnswerCorrectMapping> getCorrectMappings() {
        return correctMappings;
    }

    public void setCorrectMappings(List<ShortAnswerCorrectMapping> correctMappings) {
        this.correctMappings = correctMappings != null ? correctMappings : new ArrayList<>();
    }

    @Override
    public Set<Long> componentIds() {
        Set<Long> ids = new HashSet<>();
        for (ShortAnswerSpot spot : spots) {
            if (spot.getId() != null) {
                ids.add(spot.getId());
            }
        }
        for (ShortAnswerSolution solution : solutions) {
            if (solution.getId() != null) {
                ids.add(solution.getId());
            }
        }
        for (ShortAnswerCorrectMapping mapping : correctMappings) {
            if (mapping.getId() != null) {
                ids.add(mapping.getId());
            }
        }
        return ids;
    }

    /**
     * Value equality by persisted JSON. See {@link QuizQuestionContent#haveEqualPersistedForm} for why this is needed
     * and why it is not delegated to the nested components.
     *
     * @param other the object to compare with
     * @return true if both would be persisted as the same JSON
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof ShortAnswerQuestionContent && QuizQuestionContent.haveEqualPersistedForm(this, (ShortAnswerQuestionContent) other);
    }

    @Override
    public int hashCode() {
        return QuizQuestionContent.persistedFormHashCode(this);
    }
}
