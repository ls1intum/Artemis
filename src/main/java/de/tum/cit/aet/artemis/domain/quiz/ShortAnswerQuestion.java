package de.tum.cit.aet.artemis.domain.quiz;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.domain.quiz.scoring.ScoringStrategy;
import de.tum.cit.aet.artemis.domain.quiz.scoring.ScoringStrategyShortAnswerAllOrNothing;
import de.tum.cit.aet.artemis.domain.quiz.scoring.ScoringStrategyShortAnswerProportionalWithPenalty;
import de.tum.cit.aet.artemis.domain.quiz.scoring.ScoringStrategyShortAnswerProportionalWithoutPenalty;
import de.tum.cit.aet.artemis.domain.view.QuizView;

/**
 * A ShortAnswerQuestion.
 */
@Entity
@DiscriminatorValue(value = "SA")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerQuestion extends QuizQuestion {

    // TODO: making this a bidirectional relation leads to weird Hibernate behavior with missing data when loading quiz questions, we should investigate this again in the future
    // after 6.x upgrade
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "question_id")
    @OrderColumn
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<ShortAnswerSpot> spots = new ArrayList<>();

    // TODO: making this a bidirectional relation leads to weird Hibernate behavior with missing data when loading quiz questions, we should investigate this again in the future
    // after 6.x upgrade
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "question_id")
    @OrderColumn
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private List<ShortAnswerSolution> solutions = new ArrayList<>();

    // TODO: making this a bidirectional relation leads to weird Hibernate behavior with missing data when loading quiz questions, we should investigate this again in the future
    // after 6.x upgrade
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "question_id")
    @OrderColumn
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.After.class)
    private List<ShortAnswerMapping> correctMappings = new ArrayList<>();

    @Column(name = "similarity_value")
    @JsonView(QuizView.Before.class)
    private Integer similarityValue = 85;

    @Column(name = "match_letter_case")
    @JsonView(QuizView.Before.class)
    private Boolean matchLetterCase = false;

    public List<ShortAnswerSpot> getSpots() {
        return spots;
    }

    public void setSpots(List<ShortAnswerSpot> shortAnswerSpots) {
        this.spots = shortAnswerSpots;
    }

    public List<ShortAnswerSolution> getSolutions() {
        return solutions;
    }

    public ShortAnswerQuestion addSolution(ShortAnswerSolution shortAnswerSolution) {
        this.solutions.add(shortAnswerSolution);
        shortAnswerSolution.setQuestion(this);
        return this;
    }

    public ShortAnswerQuestion removeSolution(ShortAnswerSolution shortAnswerSolution) {
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

    public ShortAnswerQuestion addCorrectMapping(ShortAnswerMapping shortAnswerMapping) {
        this.correctMappings.add(shortAnswerMapping);
        shortAnswerMapping.setQuestion(this);
        return this;
    }

    public ShortAnswerQuestion removeCorrectMapping(ShortAnswerMapping shortAnswerMapping) {
        this.correctMappings.remove(shortAnswerMapping);
        shortAnswerMapping.setQuestion(null);
        return this;
    }

    public void setCorrectMappings(List<ShortAnswerMapping> shortAnswerMappings) {
        this.correctMappings = shortAnswerMappings;
    }

    public Integer getSimilarityValue() {
        return this.similarityValue;
    }

    public void setSimilarityValue(Integer similarityValue) {
        this.similarityValue = similarityValue;
    }

    public Boolean matchLetterCase() {
        return this.matchLetterCase;
    }

    public void setMatchLetterCase(Boolean matchLetterCase) {
        this.matchLetterCase = matchLetterCase;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

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
            return checkSolutionsIfRecalculationIsNecessary(shortAnswerOriginalQuestion) || checkSpotsIfRecalculationIsNecessary(shortAnswerOriginalQuestion)
                    || !getCorrectMappings().equals(shortAnswerOriginalQuestion.getCorrectMappings());
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
