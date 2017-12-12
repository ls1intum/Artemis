package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * A QuizPointStatistic.
 */
@Entity
@DiscriminatorValue(value="QP")
public class QuizPointStatistic extends Statistic implements Serializable {

    private static final long serialVersionUID = 1L;


    @OneToMany(cascade=CascadeType.ALL, fetch=FetchType.EAGER, orphanRemoval=true, mappedBy = "quizPointStatistic")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<PointCounter> pointCounters = new HashSet<>();

    @OneToOne(mappedBy = "quizPointStatistic")
    @JsonIgnore
    private QuizExercise quiz;

    public Set<PointCounter> getPointCounters() {
        return pointCounters;
    }

    public QuizPointStatistic pointCounters(Set<PointCounter> pointCounters) {
        this.pointCounters = pointCounters;
        return this;
    }

    public QuizPointStatistic addPointCounters(PointCounter pointCounter) {
        this.pointCounters.add(pointCounter);
        pointCounter.setQuizPointStatistic(this);
        return this;
    }

    public QuizPointStatistic removePointCounters(PointCounter pointCounter) {
        this.pointCounters.remove(pointCounter);
        pointCounter.setQuizPointStatistic(null);
        return this;
    }

    public void setPointCounters(Set<PointCounter> pointCounters) {
        this.pointCounters = pointCounters;
    }

    public QuizExercise getQuiz() {
        return quiz;
    }

    public QuizPointStatistic quiz(QuizExercise quizExercise) {
        this.quiz = quizExercise;
        return this;
    }

    public void setQuiz(QuizExercise quizExercise) {
        this.quiz = quizExercise;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QuizPointStatistic quizPointStatistic = (QuizPointStatistic) o;
        if (quizPointStatistic.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), quizPointStatistic.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "QuizPointStatistic{" +
            "id=" + getId() +
            "}";
    }
    /**
     * 1. creates the PointCounter for the new score
     *          if there is already an PointCounter with the given score -> nothing happens
     *
     * @param score the score which will be added to the QuizPointStatistic
     */
    public void addScore(Double score) {

        if(score ==null) {
            return;
        }
        // check if score as an associated PointCounter: true: do nothing, false: add one
        for(PointCounter counter: pointCounters) {
            if(score.equals(counter.getPoints())) {
                return;
            }
        }
        PointCounter pointCounter = new PointCounter();
        pointCounter.setPoints(score);
        addPointCounters(pointCounter);

    }

    /**
     * 1. check if the Result is rated or unrated
     * 2. increase participants and the PointCounter, which is associated to the score
     *
     * @param score whose PointCounter increases
     * @param rated specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise)
     *                                  or unrated  ( participated after the dueDate of the quizExercise)
     */
    public void addResult(Long score, boolean rated) {

        if(score == null) {
            return;
        }

        //convert score into points
        Double points = (double) Math.round(((double) quiz.getMaxTotalScore()) * ((double) score/100));

        if(rated) {
            //increase rated participants
            setParticipantsRated(getParticipantsRated()+1);

            //find associated rated pointCounter and increase it
            for (PointCounter pointCounter: pointCounters) {
                if(points.equals(pointCounter.getPoints())) {
                    pointCounter.setRatedCounter(pointCounter.getRatedCounter()+1);
                }
            }
        }
        // Result is unrated
        else{
            //increase unrated participants
            setParticipantsUnrated(getParticipantsUnrated()+1);

            //find associated unrated pointCounter and increase it
            for (PointCounter pointCounter: pointCounters) {
                if(points.equals(pointCounter.getPoints())) {
                    pointCounter.setUnRatedCounter(pointCounter.getUnRatedCounter()+1);
                }
            }
        }
    }

    /**
     * 1. check if the Result is rated or unrated
     * 2. decrease participants and the PointCounter, which is associated to the score
     *
     * @param score whose PointCounter increases
     * @param rated specify if the Result was rated ( participated during the releaseDate and the dueDate of the quizExercise)
     *                                  or unrated  ( participated after the dueDate of the quizExercise)
     */
    public void removeOldResult(Long score ,boolean rated ) {

        if(score == null) {
            return;
        }

        Double points = (double) Math.round(((double) quiz.getMaxTotalScore()) * ((double) score/100));

        if(rated) {
            //decrease rated participants
            setParticipantsRated(getParticipantsRated()-1);

            //find associated rated pointCounter and decrease it
            for (PointCounter pointCounter: pointCounters) {
                if(points.equals(pointCounter.getPoints())) {
                    pointCounter.setRatedCounter(pointCounter.getRatedCounter()-1);
                }
            }
        }
        else{
            //decrease unrated participants
            setParticipantsUnrated(getParticipantsUnrated()-1);

            //find associated unrated pointCounter and decrease it
            for (PointCounter pointCounter: pointCounters) {
                if(points.equals(pointCounter.getPoints())) {
                    pointCounter.setUnRatedCounter(pointCounter.getUnRatedCounter()-1);
                }
            }
        }
    }
}
