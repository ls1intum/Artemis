package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A QuizPointStatistic.
 */
@Entity
@DiscriminatorValue(value = "QP")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class QuizPointStatistic extends QuizStatistic {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "counters")
    private List<PointCounter> pointCounters = new ArrayList<>();

    @OneToOne(mappedBy = "quizPointStatistic", fetch = FetchType.LAZY)
    @JsonIgnore
    private QuizExercise quiz;

    public List<PointCounter> getPointCounters() {
        return pointCounters;
    }

    /**
     * Adds a point counter and assigns a statistic-scoped id when necessary.
     *
     * @param pointCounter the point counter to add
     * @return this statistic
     */
    public QuizPointStatistic addPointCounters(PointCounter pointCounter) {
        if (pointCounter.getId() == null) {
            pointCounter.setId(nextPointCounterId());
        }
        pointCounters.add(pointCounter);
        pointCounters.sort(Comparator.comparingDouble(PointCounter::getPoints));
        return this;
    }

    public QuizPointStatistic removePointCounters(PointCounter pointCounter) {
        pointCounters.remove(pointCounter);
        return this;
    }

    public void setPointCounters(List<PointCounter> pointCounters) {
        this.pointCounters = pointCounters == null ? new ArrayList<>() : new ArrayList<>(pointCounters);
        this.pointCounters.removeIf(pointCounter -> pointCounter == null);
        assignMissingCounterIds();
        this.pointCounters.sort(Comparator.comparingDouble(PointCounter::getPoints));
    }

    private void assignMissingCounterIds() {
        for (PointCounter pointCounter : pointCounters) {
            if (pointCounter.getId() == null) {
                pointCounter.setId(nextPointCounterId());
            }
        }
    }

    private long nextPointCounterId() {
        long highestId = 0;
        for (PointCounter pointCounter : pointCounters) {
            if (pointCounter.getId() != null) {
                highestId = Math.max(highestId, pointCounter.getId());
            }
        }
        return Math.incrementExact(highestId);
    }

    public QuizExercise getQuiz() {
        return quiz;
    }

    public void setQuiz(QuizExercise quizExercise) {
        this.quiz = quizExercise;
    }

    @Override
    public String toString() {
        return "QuizPointStatistic{" + "id=" + getId() + ", counters=" + getPointCounters() + "}";
    }

    /**
     * 1. creates the PointCounter for the new score if there is already an PointCounter with the given score -> nothing happens
     *
     * @param score the score which will be added to the QuizPointStatistic
     */
    public void addScore(Double score) {

        if (score == null) {
            return;
        }
        // check if score as an associated PointCounter: true: do nothing, false: add one
        for (PointCounter counter : pointCounters) {
            if (score.equals(counter.getPoints())) {
                return;
            }
        }
        PointCounter pointCounter = new PointCounter();
        pointCounter.setPoints(score);
        addPointCounters(pointCounter);
    }

    /**
     * increase participants and the PointCounter, which is associated to the score
     *
     * @param score whose PointCounter increases
     * @param rated specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise) or unrated ( participated after the dueDate of the
     *                  quizExercise)
     */
    public void addResult(Double score, Boolean rated) {
        if (score == null) {
            return;
        }
        changeStatisticBasedOnResult(score, rated, 1);
    }

    /**
     * decrease participants and the PointCounter, which is associated to the score
     *
     * @param score whose PointCounter decreases
     * @param rated specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise) or unrated ( participated after the dueDate of the
     *                  quizExercise)
     */
    public void removeOldResult(Double score, Boolean rated) {
        if (score == null) {
            return;
        }
        changeStatisticBasedOnResult(score, rated, -1);
    }

    /**
     * 1. check if the Result is rated or unrated 2. change participants and the PointCounter, which is associated to the score
     *
     * @param score       whose PointCounter decreases
     * @param rated       specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise) or unrated ( participated after the dueDate of
     *                        the quizExercise)
     * @param countChange the int-value, which will be added to the Counter and participants
     */
    private void changeStatisticBasedOnResult(double score, Boolean rated, int countChange) {
        /*
         * RoundingUtil#roundScoreSpecifiedByCourseSettings is not applicable here,
         * as we need to sort the points into existing integer buckets
         */
        double points = Math.round(quiz.getOverallQuizPoints() * (score / 100));

        if (Boolean.TRUE.equals(rated)) {
            // change rated participants
            setParticipantsRated(getParticipantsRated() + countChange);

            // find associated rated pointCounter and change it
            for (PointCounter pointCounter : pointCounters) {
                if (points == pointCounter.getPoints()) {
                    pointCounter.setRatedCounter(pointCounter.getRatedCounter() + countChange);
                }
            }
        }
        else {
            // change unrated participants
            setParticipantsUnrated(getParticipantsUnrated() + countChange);

            // find associated unrated pointCounter and change it
            for (PointCounter pointCounter : pointCounters) {
                if (points == pointCounter.getPoints()) {
                    pointCounter.setUnRatedCounter(pointCounter.getUnRatedCounter() + countChange);
                }
            }
        }
    }

    /**
     * Reset all counters to 0
     */
    public void resetStatistic() {
        setParticipantsUnrated(0);
        setParticipantsRated(0);
        for (PointCounter pointCounter : pointCounters) {
            pointCounter.setRatedCounter(0);
            pointCounter.setUnRatedCounter(0);
        }
    }
}
