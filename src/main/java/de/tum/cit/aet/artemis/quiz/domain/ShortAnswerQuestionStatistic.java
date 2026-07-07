package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A ShortAnswerQuestionStatistic.
 * <p>
 * Its per-spot counters are stored as a JSON list in the {@code quiz_statistic.counters} column (see {@link ShortAnswerSpotCounter}) instead of separate
 * {@code quiz_statistic_counter} rows, eliminating the eager {@code @OneToMany} counter fan-out. Counters are fully recomputed from the results on every statistics update, so no
 * per-counter locking is required. {@link #getShortAnswerSpotCounters()} keeps its shape so the REST/websocket wire format is preserved. Mirrors
 * {@link DragAndDropQuestionStatistic}.
 */
@Entity
@DiscriminatorValue(value = "SA")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerQuestionStatistic extends QuizQuestionStatistic {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "counters")
    private List<ShortAnswerSpotCounter> shortAnswerSpotCounters = new ArrayList<>();

    public List<ShortAnswerSpotCounter> getShortAnswerSpotCounters() {
        return shortAnswerSpotCounters;
    }

    public void addShortAnswerSpotCounters(ShortAnswerSpotCounter shortAnswerSpotCounter) {
        this.shortAnswerSpotCounters.add(shortAnswerSpotCounter);
    }

    public void setShortAnswerSpotCounters(List<ShortAnswerSpotCounter> shortAnswerSpotCounters) {
        this.shortAnswerSpotCounters = shortAnswerSpotCounters != null ? shortAnswerSpotCounters : new ArrayList<>();
    }

    /**
     * 1. creates the ShortAnswerSpotCounter for the new spot if there is already a ShortAnswerSpotCounter with the given spot -> nothing happens
     *
     * @param spot the spot-object which will be added to the ShortAnswerQuestionStatistic
     */
    public void addSpot(ShortAnswerSpot spot) {
        if (spot == null || spot.getId() == null) {
            return;
        }

        for (ShortAnswerSpotCounter counter : shortAnswerSpotCounters) {
            if (spot.getId().equals(counter.getSpotId())) {
                return;
            }
        }
        ShortAnswerSpotCounter spotCounter = new ShortAnswerSpotCounter();
        spotCounter.setSpotId(spot.getId());
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
        ShortAnswerQuestion question = getQuizQuestion() instanceof ShortAnswerQuestion shortAnswerQuestion ? shortAnswerQuestion : null;

        if (rated) {
            // change the rated participants
            setParticipantsRated(getParticipantsRated() + change);
            // change rated spotCounter if spot is correct
            for (ShortAnswerSpotCounter spotCounter : correctSpotCounters(question, shortAnswerSubmittedAnswer)) {
                spotCounter.setRatedCounter(spotCounter.getRatedCounter() + change);
            }
            // change rated correctCounter if answer is complete correct
            if (getQuizQuestion().isAnswerCorrect(shortAnswerSubmittedAnswer)) {
                setRatedCorrectCounter(getRatedCorrectCounter() + change);
            }
        }
        // Result is unrated
        else {
            // change the unrated participants
            setParticipantsUnrated(getParticipantsUnrated() + change);
            // change unrated spotCounter if spot is correct
            for (ShortAnswerSpotCounter spotCounter : correctSpotCounters(question, shortAnswerSubmittedAnswer)) {
                spotCounter.setUnRatedCounter(spotCounter.getUnRatedCounter() + change);
            }
            // change unrated correctCounter if answer is complete correct
            if (getQuizQuestion().isAnswerCorrect(shortAnswerSubmittedAnswer)) {
                setUnRatedCorrectCounter(getUnRatedCorrectCounter() + change);
            }
        }
    }

    /**
     * Determine, for the given submitted answer, which spot counters correspond to a correctly answered spot. Resolves each counter's spot by its question-scoped id against the
     * owning question (counters no longer hold a spot object) and reuses the stored {@code isCorrect} flag together with a fuzzy re-check of the submitted text.
     *
     * @param question                   the owning question (may be null on a transient statistic)
     * @param shortAnswerSubmittedAnswer the submitted answer
     * @return the spot counters whose spot was answered correctly
     */
    private List<ShortAnswerSpotCounter> correctSpotCounters(ShortAnswerQuestion question, ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
        List<ShortAnswerSpotCounter> correct = new ArrayList<>();
        if (question == null) {
            return correct;
        }
        for (ShortAnswerSpotCounter spotCounter : shortAnswerSpotCounters) {
            ShortAnswerSpot spot = question.findSpotById(spotCounter.getSpotId());
            if (spot == null) {
                continue;
            }
            ShortAnswerSubmittedText shortAnswerSubmittedText = shortAnswerSubmittedAnswer.getSubmittedTextForSpot(spot);
            if (shortAnswerSubmittedText == null) {
                continue;
            }
            // reconnect to avoid issues
            shortAnswerSubmittedText.setSubmittedAnswer(shortAnswerSubmittedAnswer);
            Set<ShortAnswerSolution> shortAnswerSolutions = question.getCorrectSolutionForSpot(spot);
            for (ShortAnswerSolution solution : shortAnswerSolutions) {
                if (shortAnswerSubmittedText.isSubmittedTextCorrect(shortAnswerSubmittedText.getText(), solution.getText())
                        && Boolean.TRUE.equals(shortAnswerSubmittedText.isIsCorrect())) {
                    correct.add(spotCounter);
                    break;
                }
            }
        }
        return correct;
    }
}
