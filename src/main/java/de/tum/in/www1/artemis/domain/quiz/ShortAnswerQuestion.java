package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.*;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A ShortAnswerQuestion.
 */
@Entity
@DiscriminatorValue(value = "SA")
@JsonTypeName("short-answer")
public class ShortAnswerQuestion extends QuizQuestion implements Serializable {

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
    public Boolean isValid() {
        // check general validity (using superclass)
        if (!super.isValid()) {
            return false;
        }

        // check if at least one correct mapping exists
        return getCorrectMappings() != null && !getCorrectMappings().isEmpty();

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
        if (originalQuizQuestion instanceof ShortAnswerQuestion) {
            ShortAnswerQuestion shortAnswerOriginalQuestion = (ShortAnswerQuestion) originalQuizQuestion;
            undoUnallowedSpotChanges(shortAnswerOriginalQuestion);
        }
    }

    /**
     * undo all solution-changes which are not allowed ( adding them)
     *
     * @param originalQuestion the original ShortAnswer-object, which will be compared with this question
     */
    private void undoUnallowedSolutionChanges(ShortAnswerQuestion originalQuestion) {

        // find added solutions, which are not allowed to be added
        Set<ShortAnswerSolution> notAllowedAddedSolutions = new HashSet<>();
        // check every solution of the question
        for (ShortAnswerSolution solution : this.getSolutions()) {
            // check if the solution were already in the originalQuestion -> if not it's an added solution
            if (originalQuestion.getSolutions().contains(solution)) {
                // find original solution
                ShortAnswerSolution originalSolution = originalQuestion.findSolutionById(solution.getId());
                // correct invalid = null to invalid = false
                if (solution.isInvalid() == null) {
                    solution.setInvalid(false);
                }
                // reset invalid solution if it already set to true (it's not possible to set a solution valid again)
                solution.setInvalid(solution.isInvalid() || (originalSolution.isInvalid() != null && originalSolution.isInvalid()));
            }
            else {
                // mark the added solution (adding solutions is not allowed)
                notAllowedAddedSolutions.add(solution);
            }
        }
        // remove the added solutions
        this.getSolutions().removeAll(notAllowedAddedSolutions);
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

    @Override
    public boolean isUpdateOfResultsAndStatisticsNecessary(QuizQuestion originalQuizQuestion) {
        if (originalQuizQuestion instanceof ShortAnswerQuestion) {
            ShortAnswerQuestion shortAnswerOriginalQuestion = (ShortAnswerQuestion) originalQuizQuestion;
            return checkSolutionsIfRecalculationIsNecessary(shortAnswerOriginalQuestion) || checkSpotsIfRecalculationIsNecessary(shortAnswerOriginalQuestion)
                    || !getCorrectMappings().equals(shortAnswerOriginalQuestion.getCorrectMappings());
        }
        return false;
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
        return "ShortAnswerQuestion{" + "id=" + getId() + "}";
    }

    @Override
    public QuizQuestion copyQuestionId() {
        var question = new ShortAnswerQuestion();
        question.setId(getId());
        return question;
    }
}
