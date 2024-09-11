package de.tum.cit.aet.artemis.domain.quiz;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A ShortAnswerQuestionStatistic.
 */
@Entity
@DiscriminatorValue(value = "SA")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerQuestionStatistic extends QuizQuestionStatistic {

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "shortAnswerQuestionStatistic")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<ShortAnswerSpotCounter> shortAnswerSpotCounters = new HashSet<>();

    public Set<ShortAnswerSpotCounter> getShortAnswerSpotCounters() {
        return shortAnswerSpotCounters;
    }

    public void addShortAnswerSpotCounters(ShortAnswerSpotCounter shortAnswerSpotCounter) {
        this.shortAnswerSpotCounters.add(shortAnswerSpotCounter);
        shortAnswerSpotCounter.setShortAnswerQuestionStatistic(this);
    }

    public void setShortAnswerSpotCounters(Set<ShortAnswerSpotCounter> shortAnswerSpotCounters) {
        this.shortAnswerSpotCounters = shortAnswerSpotCounters;
    }

    /**
     * 1. creates the ShortAnswerSpotCounter for the new spot if where is already an ShortAnswerSpotCounter with the given spot -> nothing happens
     *
     * @param spot the spot-object which will be added to the ShortAnswerQuestionStatistic
     */
    public void addSpot(ShortAnswerSpot spot) {
        if (spot == null) {
            return;
        }

        for (ShortAnswerSpotCounter counter : shortAnswerSpotCounters) {
            if (spot.equals(counter.getSpot())) {
                return;
            }
        }
        ShortAnswerSpotCounter spotCounter = new ShortAnswerSpotCounter();
        spotCounter.setSpot(spot);
        addShortAnswerSpotCounters(spotCounter);
    }

    @Override
    public void resetStatistic() {
        super.resetStatistic();
        for (ShortAnswerSpotCounter spotCounter : shortAnswerSpotCounters) {
            spotCounter.setRatedCounter(0);
            spotCounter.setUnRatedCounter(0);
        }
    }

    /**
     * 1. check if the Result is rated or unrated 2. change participants, all the ShortAnswerSpotCounter if the ShortAnswerAssignment is correct and if the complete question is
     * correct, than change the correctCounter
     *
     * @param submittedAnswer the submittedAnswer object which contains all submittedTexts
     * @param rated           specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise) or unrated ( participated after the dueDate
     *                            of the quizExercise)
     * @param change          the int-value, which will be added to the Counter and participants
     */
    @Override
    protected void changeStatisticBasedOnResult(SubmittedAnswer submittedAnswer, boolean rated, int change) {
        if (!(submittedAnswer instanceof ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer)) {
            return;
        }

        if (rated) {
            // change the rated participants
            setParticipantsRated(getParticipantsRated() + change);
            handleCountersForCorrectSpots(shortAnswerSubmittedAnswer, (ShortAnswerSpotCounter spotCounter) -> {
                // change rated spotCounter if spot is correct
                spotCounter.setRatedCounter(spotCounter.getRatedCounter() + change);
            });

            // change rated correctCounter if answer is complete correct
            if (getQuizQuestion().isAnswerCorrect(shortAnswerSubmittedAnswer)) {
                setRatedCorrectCounter(getRatedCorrectCounter() + change);
            }
        }
        // Result is unrated
        else {
            // change the unrated participants
            setParticipantsUnrated(getParticipantsUnrated() + change);
            handleCountersForCorrectSpots(shortAnswerSubmittedAnswer, (ShortAnswerSpotCounter spotCounter) -> {
                // change unrated spotCounter if spot is correct
                spotCounter.setUnRatedCounter(spotCounter.getUnRatedCounter() + change);
            });
            // change unrated correctCounter if answer is complete correct
            if (getQuizQuestion().isAnswerCorrect(shortAnswerSubmittedAnswer)) {
                setUnRatedCorrectCounter(getUnRatedCorrectCounter() + change);
            }
        }
    }

    private void handleCountersForCorrectSpots(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer, Consumer<ShortAnswerSpotCounter> changeCounterIfSpotIsCorrect) {
        if (shortAnswerSubmittedAnswer.getSubmittedTexts() != null) {
            for (ShortAnswerSpotCounter spotCounter : shortAnswerSpotCounters) {
                ShortAnswerSpot spot = spotCounter.getSpot();
                ShortAnswerSubmittedText shortAnswerSubmittedText = shortAnswerSubmittedAnswer.getSubmittedTextForSpot(spot);
                Set<ShortAnswerSolution> shortAnswerSolutions = spotCounter.getSpot().getQuestion().getCorrectSolutionForSpot(spot);

                if (shortAnswerSubmittedText == null) {
                    continue;
                }
                // reconnect to avoid issues
                shortAnswerSubmittedText.setSubmittedAnswer(shortAnswerSubmittedAnswer);
                for (ShortAnswerSolution solution : shortAnswerSolutions) {
                    if (shortAnswerSubmittedText.isSubmittedTextCorrect(shortAnswerSubmittedText.getText(), solution.getText())
                            && Boolean.TRUE.equals(shortAnswerSubmittedText.isIsCorrect())) {
                        changeCounterIfSpotIsCorrect.accept(spotCounter);
                    }
                }
            }
        }
    }
}
