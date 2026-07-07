package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategy;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyShortAnswerAllOrNothing;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyShortAnswerProportionalWithPenalty;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyShortAnswerProportionalWithoutPenalty;

/**
 * A ShortAnswerQuestion.
 * <p>
 * Its spots, solutions and correct mappings are no longer separate JPA entity tables: they are stored inside the {@code quiz_question.content} JSON column as a
 * {@link ShortAnswerQuestionContent}. This eliminates the eager {@code @OneToMany} fan-out. The public accessors below ({@code getSpots()} / {@code getSolutions()} /
 * {@code getCorrectMappings()}) keep their original signatures and delegate to the content, so the REST/websocket wire format and all callers are preserved. Correct mappings are
 * stored normalized (id-based, see {@link ShortAnswerCorrectMapping}) and resolved to object-based {@link ShortAnswerMapping}s on access. Mirrors {@link DragAndDropQuestion}.
 */
@Entity
@DiscriminatorValue(value = "SA")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerQuestion extends QuizQuestion {

    @Column(name = "similarity_value")
    private Integer similarityValue = 85;

    @Column(name = "match_letter_case")
    private Boolean matchLetterCase = false;

    /**
     * @return the short-answer content, creating and attaching an empty one if none exists yet.
     */
    private ShortAnswerQuestionContent saContent() {
        if (getContent() instanceof ShortAnswerQuestionContent shortAnswerContent) {
            return shortAnswerContent;
        }
        ShortAnswerQuestionContent created = new ShortAnswerQuestionContent();
        setContent(created);
        return created;
    }

    /**
     * Mint a fresh, question-scoped component id: one greater than the largest id currently used by any spot, solution or correct mapping of this question.
     *
     * @return the next free component id
     */
    private long nextComponentId() {
        long max = 0;
        for (Long id : saContent().componentIds()) {
            if (id != null && id > max) {
                max = id;
            }
        }
        return max + 1;
    }

    /**
     * Assign a fresh, question-scoped id to every component in the given list that does not have one yet. Called from the entity-level bulk setters used by the create/edit/import
     * flows; the JSON deserialization path goes through {@link ShortAnswerQuestionContent}'s own setters instead and therefore preserves existing ids.
     *
     * @param components the components to assign ids to
     */
    private void assignMissingComponentIds(List<? extends DomainObject> components) {
        for (var component : components) {
            if (component.getId() == null) {
                component.setId(nextComponentId());
            }
        }
    }

    /**
     * Mint a fresh, question-scoped id for any spot or solution added without one (e.g. via {@code getSpots().add(...)} / {@code getSolutions().add(...)}, which bypass
     * {@link #addSpot} / {@link #addSolution}). Called before persisting so the statistics counters (keyed by spot id) and the stored JSON content stay id-consistent.
     */
    public void assignMissingComponentIds() {
        assignMissingComponentIds(getSpots());
        assignMissingComponentIds(getSolutions());
    }

    public List<ShortAnswerSpot> getSpots() {
        return saContent().getSpots();
    }

    public void setSpots(List<ShortAnswerSpot> shortAnswerSpots) {
        saContent().setSpots(shortAnswerSpots);
        assignMissingComponentIds(saContent().getSpots());
    }

    /**
     * Adds a single spot, assigning it a fresh question-scoped id if it does not have one yet.
     *
     * @param shortAnswerSpot the spot to add
     * @return this question for fluent chaining
     */
    public ShortAnswerQuestion addSpot(ShortAnswerSpot shortAnswerSpot) {
        if (shortAnswerSpot.getId() == null) {
            shortAnswerSpot.setId(nextComponentId());
        }
        saContent().getSpots().add(shortAnswerSpot);
        return this;
    }

    /**
     * Removes a single spot.
     *
     * @param shortAnswerSpot the spot to remove
     * @return this question for fluent chaining
     */
    public ShortAnswerQuestion removeSpot(ShortAnswerSpot shortAnswerSpot) {
        saContent().getSpots().remove(shortAnswerSpot);
        return this;
    }

    public List<ShortAnswerSolution> getSolutions() {
        return saContent().getSolutions();
    }

    public void setSolutions(List<ShortAnswerSolution> shortAnswerSolutions) {
        saContent().setSolutions(shortAnswerSolutions);
        assignMissingComponentIds(saContent().getSolutions());
    }

    /**
     * Adds a single solution, assigning it a fresh question-scoped id if it does not have one yet.
     *
     * @param shortAnswerSolution the solution to add
     * @return this question for fluent chaining
     */
    public ShortAnswerQuestion addSolution(ShortAnswerSolution shortAnswerSolution) {
        if (shortAnswerSolution.getId() == null) {
            shortAnswerSolution.setId(nextComponentId());
        }
        saContent().getSolutions().add(shortAnswerSolution);
        return this;
    }

    /**
     * Removes a single solution.
     *
     * @param shortAnswerSolution the solution to remove
     * @return this question for fluent chaining
     */
    public ShortAnswerQuestion removeSolution(ShortAnswerSolution shortAnswerSolution) {
        saContent().getSolutions().remove(shortAnswerSolution);
        return this;
    }

    /**
     * The correct mappings resolved into object-based {@link ShortAnswerMapping}s (with their spot / solution objects and derived indices). Built on demand from the normalized
     * id-based mappings stored in {@link ShortAnswerQuestionContent}. Mutating the returned set does not affect the stored mappings — use {@link #addCorrectMapping},
     * {@link #removeCorrectMapping} or {@link #setCorrectMappings} instead.
     *
     * @return the resolved correct mappings
     */
    public Set<ShortAnswerMapping> getCorrectMappings() {
        Set<ShortAnswerMapping> result = new HashSet<>();
        List<ShortAnswerSpot> spots = saContent().getSpots();
        List<ShortAnswerSolution> solutions = saContent().getSolutions();
        for (ShortAnswerCorrectMapping entry : saContent().getCorrectMappings()) {
            ShortAnswerSpot spot = findSpotById(entry.getSpotId());
            ShortAnswerSolution solution = findSolutionById(entry.getSolutionId());
            // skip stale mappings whose spot or solution no longer exists (e.g. removed during re-evaluation)
            if (spot == null || solution == null) {
                continue;
            }
            ShortAnswerMapping mapping = new ShortAnswerMapping();
            mapping.setId(entry.getId());
            mapping.setInvalid(entry.isInvalid());
            mapping.setSpot(spot);
            mapping.setSolution(solution);
            int spotIndex = spots.indexOf(spot);
            mapping.setShortAnswerSpotIndex(spotIndex >= 0 ? spotIndex : null);
            int solutionIndex = solutions.indexOf(solution);
            mapping.setShortAnswerSolutionIndex(solutionIndex >= 0 ? solutionIndex : null);
            result.add(mapping);
        }
        return result;
    }

    /**
     * Replaces the correct mappings, storing them id-based in the content. Missing mapping ids are minted question-scoped.
     *
     * @param shortAnswerMappings the object-based correct mappings to store
     */
    public void setCorrectMappings(Set<ShortAnswerMapping> shortAnswerMappings) {
        List<ShortAnswerCorrectMapping> entries = new ArrayList<>();
        if (shortAnswerMappings != null) {
            for (ShortAnswerMapping mapping : shortAnswerMappings) {
                entries.add(toEntry(mapping));
            }
        }
        saContent().setCorrectMappings(entries);
        // Mint ids only after the entries are attached to the content, so nextComponentId() sees each freshly-assigned id and does not hand out the same id to multiple mappings.
        for (ShortAnswerCorrectMapping entry : saContent().getCorrectMappings()) {
            if (entry.getId() == null) {
                entry.setId(nextComponentId());
            }
        }
    }

    /**
     * Adds a single correct mapping, assigning it a fresh question-scoped id if it does not have one yet. The referenced spot and solution must already have ids (i.e. be added to
     * this question first).
     *
     * @param shortAnswerMapping the correct mapping to add
     * @return this question for fluent chaining
     */
    public ShortAnswerQuestion addCorrectMapping(ShortAnswerMapping shortAnswerMapping) {
        if (shortAnswerMapping.getId() == null) {
            shortAnswerMapping.setId(nextComponentId());
        }
        saContent().getCorrectMappings().add(toEntry(shortAnswerMapping));
        return this;
    }

    /**
     * Removes the correct mapping with the same spot and solution as the given mapping.
     *
     * @param shortAnswerMapping the correct mapping to remove
     * @return this question for fluent chaining
     */
    public ShortAnswerQuestion removeCorrectMapping(ShortAnswerMapping shortAnswerMapping) {
        Long spotId = shortAnswerMapping.getSpot() != null ? shortAnswerMapping.getSpot().getId() : null;
        Long solutionId = shortAnswerMapping.getSolution() != null ? shortAnswerMapping.getSolution().getId() : null;
        saContent().getCorrectMappings().removeIf(entry -> Objects.equals(entry.getSpotId(), spotId) && Objects.equals(entry.getSolutionId(), solutionId));
        return this;
    }

    private ShortAnswerCorrectMapping toEntry(ShortAnswerMapping mapping) {
        Long spotId = mapping.getSpot() != null ? mapping.getSpot().getId() : null;
        Long solutionId = mapping.getSolution() != null ? mapping.getSolution().getId() : null;
        // id may be null here; setCorrectMappings mints missing ids after the entries are attached to the content (addCorrectMapping mints before calling this).
        return new ShortAnswerCorrectMapping(mapping.getId(), spotId, solutionId, mapping.isInvalid());
    }

    public Integer getSimilarityValue() {
        return this.similarityValue;
    }

    public void setSimilarityValue(Integer similarityValue) {
        this.similarityValue = similarityValue;
    }

    @JsonInclude
    public Boolean getMatchLetterCase() {
        return this.matchLetterCase;
    }

    public void setMatchLetterCase(Boolean matchLetterCase) {
        this.matchLetterCase = matchLetterCase;
    }

    @Override
    public Boolean isValid() {
        // check general validity (using superclass)
        if (!super.isValid()) {
            return false;
        }

        // check if at least one correct mapping exists and if similarity values are in the allowed range
        return !saContent().getCorrectMappings().isEmpty() && getSimilarityValue() >= 50 && getSimilarityValue() <= 100;

        // TODO (?): Add checks for "is solvable" and "no misleading correct mapping" --> look at the implementation in the client
    }

    /**
     * Get all solution items that are mapped to the given spot
     *
     * @param spot the spot we want to find the correct solutions for
     * @return all solutions that are defined as correct for this spot
     */
    public Set<ShortAnswerSolution> getCorrectSolutionForSpot(ShortAnswerSpot spot) {
        Set<ShortAnswerSolution> result = new HashSet<>();
        if (spot == null || spot.getId() == null) {
            return result;
        }
        for (ShortAnswerCorrectMapping mapping : saContent().getCorrectMappings()) {
            if (spot.getId().equals(mapping.getSpotId())) {
                ShortAnswerSolution solution = findSolutionById(mapping.getSolutionId());
                if (solution != null) {
                    result.add(solution);
                }
            }
        }
        return result;
    }

    /**
     * Get solution by ID
     *
     * @param solutionId the ID of the solution, which should be found
     * @return the solution with the given ID, or null if the solution is not contained in this question
     */
    public ShortAnswerSolution findSolutionById(Long solutionId) {
        if (solutionId != null) {
            for (ShortAnswerSolution solution : saContent().getSolutions()) {
                if (solutionId.equals(solution.getId())) {
                    return solution;
                }
            }
        }
        return null;
    }

    /**
     * Get spot by ID
     *
     * @param spotId the ID of the spot, which should be found
     * @return the spot with the given ID, or null if the spot is not contained in this question
     */
    public ShortAnswerSpot findSpotById(Long spotId) {
        if (spotId != null) {
            for (ShortAnswerSpot spot : saContent().getSpots()) {
                if (spotId.equals(spot.getId())) {
                    return spot;
                }
            }
        }
        return null;
    }

    @Override
    @JsonIgnore
    public void initializeStatistic() {
        setQuizQuestionStatistic(new ShortAnswerQuestionStatistic());
    }

    @Override
    public void filterForStudentsDuringQuiz() {
        super.filterForStudentsDuringQuiz();
        saContent().setCorrectMappings(new ArrayList<>());
        saContent().setSolutions(new ArrayList<>());
    }

    @Override
    public void filterForStatisticWebsocket() {
        super.filterForStatisticWebsocket();
        saContent().setCorrectMappings(new ArrayList<>());
    }

    /**
     * creates an instance of ScoringStrategy with the appropriate type for the given short answer question (based on polymorphism)
     *
     * @return an instance of the appropriate implementation of ScoringStrategy
     */
    @Override
    public ScoringStrategy makeScoringStrategy() {
        return switch (getScoringType()) {
            case ALL_OR_NOTHING -> new ScoringStrategyShortAnswerAllOrNothing();
            case PROPORTIONAL_WITH_PENALTY -> new ScoringStrategyShortAnswerProportionalWithPenalty();
            case PROPORTIONAL_WITHOUT_PENALTY -> new ScoringStrategyShortAnswerProportionalWithoutPenalty();
        };
    }

    @Override
    public String toString() {
        return "ShortAnswerQuestion{" + "id=" + getId() + "}";
    }

    @Override
    public QuizQuestion copyQuestionId() {
        var question = new ShortAnswerQuestion();
        question.setId(getId());
        return question;
    }

}
