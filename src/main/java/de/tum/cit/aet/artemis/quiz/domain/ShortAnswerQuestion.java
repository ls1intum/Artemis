package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategy;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyShortAnswerAllOrNothing;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyShortAnswerProportionalWithPenalty;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyShortAnswerProportionalWithoutPenalty;

/**
 * A ShortAnswerQuestion.
 */
@Entity
@DiscriminatorValue(value = "SA")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerQuestion extends QuizQuestion {

    // No @Cache on the three child collections below: they are the parent collections of ShortAnswerSpot / ShortAnswerSolution /
    // ShortAnswerMapping references resolved during submission merge cascade. See #12574 / #12584 for why the clustered NONSTRICT cache failed.
    // Bidirectional mapping: each child owns the question_id FK via its @ManyToOne back-reference, so a parent saveAndFlush
    // issues targeted UPDATEs on the order column instead of the DELETE+INSERT cascade that produced #12584.
    // See documentation/docs/developer/guidelines/database.mdx → "Ordered Collection with Duplicates (List)" for the
    // mandatory rules — any new @OrderColumn relationship must follow them, or pick the Set + @OrderBy alternative.
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderColumn(name = "spots_order")
    private List<ShortAnswerSpot> spots = new ArrayList<>();

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderColumn(name = "solutions_order")
    private List<ShortAnswerSolution> solutions = new ArrayList<>();

    // Stored as a Bag (List without @OrderColumn): see DragAndDropQuestion.correctMappings rationale. Position carries no
    // semantic meaning — each mapping is identified by its (spot, solution) pair. We avoid HashSet because
    // DomainObject.hashCode is id-based and breaks for transient entities. The Bag has no @OrderColumn, so Hibernate
    // does not DELETE+INSERT on parent save (the #12584 failure mode requires the unidirectional + @JoinColumn shape).
    // The legacy correct_mappings_order column on short_answer_mapping is now orphaned; tracked in #12807 for a follow-up Liquibase changeset.
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<ShortAnswerMapping> correctMappings = new ArrayList<>();

    @Column(name = "similarity_value")
    private Integer similarityValue = 85;

    @Column(name = "match_letter_case")
    private Boolean matchLetterCase = false;

    public List<ShortAnswerSpot> getSpots() {
        return spots;
    }

    public void setSpots(List<ShortAnswerSpot> shortAnswerSpots) {
        // Direct field assignment; back-references are set defensively via @PrePersist / @PreUpdate hooks.
        this.spots = shortAnswerSpots;
    }

    /**
     * Adds a single spot and maintains the bidirectional back-reference required by the {@code mappedBy} mapping.
     *
     * @param shortAnswerSpot the spot to add
     * @return this question for fluent chaining
     */
    public ShortAnswerQuestion addSpot(ShortAnswerSpot shortAnswerSpot) {
        if (this.spots == null) {
            this.spots = new ArrayList<>();
        }
        this.spots.add(shortAnswerSpot);
        shortAnswerSpot.setQuestion(this);
        return this;
    }

    /**
     * Removes a single spot and clears its back-reference; with {@code orphanRemoval = true} the spot will also be deleted on the next flush.
     *
     * @param shortAnswerSpot the spot to remove
     * @return this question for fluent chaining
     */
    public ShortAnswerQuestion removeSpot(ShortAnswerSpot shortAnswerSpot) {
        if (this.spots != null) {
            this.spots.remove(shortAnswerSpot);
        }
        shortAnswerSpot.setQuestion(null);
        return this;
    }

    public List<ShortAnswerSolution> getSolutions() {
        return solutions;
    }

    public void setSolutions(List<ShortAnswerSolution> shortAnswerSolutions) {
        // Direct field assignment; back-references are set defensively via @PrePersist / @PreUpdate hooks.
        this.solutions = shortAnswerSolutions;
    }

    /**
     * Adds a single solution and maintains the bidirectional back-reference required by the {@code mappedBy} mapping.
     *
     * @param shortAnswerSolution the solution to add
     * @return this question for fluent chaining
     */
    public ShortAnswerQuestion addSolution(ShortAnswerSolution shortAnswerSolution) {
        if (this.solutions == null) {
            this.solutions = new ArrayList<>();
        }
        this.solutions.add(shortAnswerSolution);
        shortAnswerSolution.setQuestion(this);
        return this;
    }

    /**
     * Removes a single solution and clears its back-reference; with {@code orphanRemoval = true} the solution will also be deleted on the next flush.
     *
     * @param shortAnswerSolution the solution to remove
     * @return this question for fluent chaining
     */
    public ShortAnswerQuestion removeSolution(ShortAnswerSolution shortAnswerSolution) {
        if (this.solutions != null) {
            this.solutions.remove(shortAnswerSolution);
        }
        shortAnswerSolution.setQuestion(null);
        return this;
    }

    public List<ShortAnswerMapping> getCorrectMappings() {
        return correctMappings;
    }

    public void setCorrectMappings(List<ShortAnswerMapping> shortAnswerMappings) {
        // Direct field assignment; back-references are set defensively via @PrePersist / @PreUpdate hooks.
        this.correctMappings = shortAnswerMappings;
    }

    /**
     * Adds a single mapping and maintains the bidirectional back-reference required by the {@code mappedBy} mapping.
     *
     * @param shortAnswerMapping the mapping to add
     * @return this question for fluent chaining
     */
    public ShortAnswerQuestion addCorrectMapping(ShortAnswerMapping shortAnswerMapping) {
        if (this.correctMappings == null) {
            this.correctMappings = new ArrayList<>();
        }
        this.correctMappings.add(shortAnswerMapping);
        shortAnswerMapping.setQuestion(this);
        return this;
    }

    /**
     * Removes a single mapping and clears its back-reference; with {@code orphanRemoval = true} the mapping will also be deleted on the next flush.
     *
     * @param shortAnswerMapping the mapping to remove
     * @return this question for fluent chaining
     */
    public ShortAnswerQuestion removeCorrectMapping(ShortAnswerMapping shortAnswerMapping) {
        if (this.correctMappings != null) {
            this.correctMappings.remove(shortAnswerMapping);
        }
        shortAnswerMapping.setQuestion(null);
        return this;
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
        return getCorrectMappings() != null && !getCorrectMappings().isEmpty() && getSimilarityValue() >= 50 && getSimilarityValue() <= 100;

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
        for (ShortAnswerMapping mapping : correctMappings) {
            if (mapping.getSpot().equals(spot)) {
                result.add(mapping.getSolution());
            }
        }
        return result;
    }

    /**
     * Get solution by ID
     *
     * @param solutionId the ID of the solution, which should be found
     * @return the sollution with the given ID, or null if the solution is not contained in this question
     */
    public ShortAnswerSolution findSolutionById(Long solutionId) {

        if (solutionId != null) {
            // iterate through all solutions of this quiz
            for (ShortAnswerSolution solution : solutions) {
                // return solution if the IDs are equal
                if (solution.getId().equals(solutionId)) {
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
            // iterate through all spots of this quiz
            for (ShortAnswerSpot spot : spots) {
                // return spot if the IDs are equal
                if (spot.getId().equals(spotId)) {
                    return spot;
                }
            }
        }
        return null;
    }

    /**
     * undo all solution- and spot-changes which are not allowed ( adding them)
     *
     * @param originalQuizQuestion the original QuizQuestion-object, which will be compared with this question
     */
    @Override
    public void undoUnallowedChanges(QuizQuestion originalQuizQuestion) {
        if (originalQuizQuestion instanceof ShortAnswerQuestion shortAnswerOriginalQuestion) {
            undoUnallowedSpotChanges(shortAnswerOriginalQuestion);
            checkInvalidSolutions(shortAnswerOriginalQuestion);
        }
    }

    /**
     * undo all spot-changes which are not allowed ( adding them)
     *
     * @param originalQuestion the original spot-object, which will be compared with this question
     */
    private void undoUnallowedSpotChanges(ShortAnswerQuestion originalQuestion) {

        // find added spots, which are not allowed to be added
        Set<ShortAnswerSpot> notAllowedAddedSpots = new HashSet<>();
        // check every spot of the question
        for (ShortAnswerSpot spot : this.getSpots()) {
            // check if the spot were already in the originalQuestion -> if not it's an added spot
            if (originalQuestion.getSpots().contains(spot)) {
                // find original spot
                ShortAnswerSpot originalSpot = originalQuestion.findSpotById(spot.getId());
                // correct invalid = null to invalid = false
                if (spot.isInvalid() == null) {
                    spot.setInvalid(false);
                }
                // reset invalid spot if it already set to true (it's not possible to set a spot valid again)
                spot.setInvalid(spot.isInvalid() || (originalSpot.isInvalid() != null && originalSpot.isInvalid()));
            }
            else {
                // mark the added spot (adding spots is not allowed)
                notAllowedAddedSpots.add(spot);
            }
        }
        // remove the added spots
        this.getSpots().removeAll(notAllowedAddedSpots);
    }

    /**
     * check all solutions for unset invalid states or state changes
     *
     * @param originalQuestion the original ShortAnswer-object, which will be compared with this question
     */
    private void checkInvalidSolutions(ShortAnswerQuestion originalQuestion) {
        // check every solution of the question
        for (ShortAnswerSolution solution : this.getSolutions()) {
            // correct invalid = null to invalid = false
            if (solution.isInvalid() == null) {
                solution.setInvalid(false);
            }
            ShortAnswerSolution originalSolution = originalQuestion.findSolutionById(solution.getId());
            // reset invalid solution if it was already set to true (it's not possible to set a solution valid again)
            solution.setInvalid(solution.isInvalid() || (originalSolution != null && originalSolution.isInvalid() != null && originalSolution.isInvalid()));
        }
    }

    @Override
    public boolean isUpdateOfResultsAndStatisticsNecessary(QuizQuestion originalQuizQuestion) {
        if (originalQuizQuestion instanceof ShortAnswerQuestion shortAnswerOriginalQuestion) {
            // correctMappings is a Bag (List without @OrderColumn); Hibernate may return rows in any order on reload,
            // so use Set equality to avoid spuriously triggering recalculation when the only difference is row order.
            return checkSolutionsIfRecalculationIsNecessary(shortAnswerOriginalQuestion) || checkSpotsIfRecalculationIsNecessary(shortAnswerOriginalQuestion)
                    || !new HashSet<>(getCorrectMappings()).equals(new HashSet<>(shortAnswerOriginalQuestion.getCorrectMappings()));
        }
        return false;
    }

    @Override
    @JsonIgnore
    public void initializeStatistic() {
        setQuizQuestionStatistic(new ShortAnswerQuestionStatistic());
    }

    /**
     * check solutions if an update of the Results and Statistics is necessary
     *
     * @param originalQuestion the original ShortAnswerQuestion-object, which will be compared with this question
     * @return a boolean which is true if the solution-changes make an update necessary and false if not
     */
    private boolean checkSolutionsIfRecalculationIsNecessary(ShortAnswerQuestion originalQuestion) {

        boolean updateNecessary = false;

        // check every solution of the question
        for (ShortAnswerSolution solution : this.getSolutions()) {
            // check if the solution were already in the originalQuizExercise
            if (originalQuestion.getSolutions().contains(solution)) {
                // find original solution
                ShortAnswerSolution originalSolution = originalQuestion.findSolutionById(solution.getId());

                // check if a solution is set invalid
                // if true an update of the Statistics and Results is necessary
                if ((solution.isInvalid() && !this.isInvalid() && originalSolution.isInvalid() == null)
                        || (solution.isInvalid() && !this.isInvalid() && !originalSolution.isInvalid())) {
                    updateNecessary = true;
                }
            }
        }
        // check if a solution was deleted (not allowed added solution are not relevant)
        // if true an update of the Statistics and Results is necessary
        if (this.getSolutions().size() < originalQuestion.getSolutions().size()) {
            updateNecessary = true;
        }
        return updateNecessary;
    }

    /**
     * check spots if an update of the Results and Statistics is necessary
     *
     * @param originalQuestion the original ShortAnswerQuestion-object, which will be compared with this question
     * @return a boolean which is true if the spot-changes make an update necessary and false if not
     */
    private boolean checkSpotsIfRecalculationIsNecessary(ShortAnswerQuestion originalQuestion) {

        boolean updateNecessary = false;

        // check every spot of the question
        for (ShortAnswerSpot spot : this.getSpots()) {
            // check if the spot were already in the originalQuizExercise
            if (originalQuestion.getSpots().contains(spot)) {
                // find original spot
                ShortAnswerSpot originalSpot = originalQuestion.findSpotById(spot.getId());

                // check if a spot is set invalid
                // if true an update of the Statistics and Results is necessary
                if ((spot.isInvalid() && !this.isInvalid() && originalSpot.isInvalid() == null) || (spot.isInvalid() && !this.isInvalid() && !originalSpot.isInvalid())) {
                    updateNecessary = true;
                }
            }
        }
        // check if a spot was deleted (not allowed added spots are not relevant)
        // if true an update of the Statistics and Results is necessary
        if (this.getSpots().size() < originalQuestion.getSpots().size()) {
            updateNecessary = true;
        }
        return updateNecessary;
    }

    /**
     * Defensive back-reference fixup: with bidirectional mappedBy the child @ManyToOne owns the FK, so any child added
     * via {@code getSpots().add(...)} / {@code getSolutions().add(...)} / {@code getCorrectMappings().add(...)}
     * (bypassing the helpers) would otherwise INSERT with {@code question_id = NULL}.
     */
    @PrePersist
    @PreUpdate
    private void ensureChildBackReferences() {
        if (spots != null) {
            for (ShortAnswerSpot spot : spots) {
                if (spot != null && spot.getQuestion() != this) {
                    spot.setQuestion(this);
                }
            }
        }
        if (solutions != null) {
            for (ShortAnswerSolution solution : solutions) {
                if (solution != null && solution.getQuestion() != this) {
                    solution.setQuestion(this);
                }
            }
        }
        if (correctMappings != null) {
            for (ShortAnswerMapping mapping : correctMappings) {
                if (mapping != null && mapping.getQuestion() != this) {
                    mapping.setQuestion(this);
                }
            }
        }
    }

    @Override
    public void filterForStudentsDuringQuiz() {
        super.filterForStudentsDuringQuiz();
        setCorrectMappings(null);
        setSolutions(null);
    }

    @Override
    public void filterForStatisticWebsocket() {
        super.filterForStatisticWebsocket();
        setCorrectMappings(null);
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
