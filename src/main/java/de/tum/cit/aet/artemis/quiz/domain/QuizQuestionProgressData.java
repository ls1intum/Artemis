package de.tum.cit.aet.artemis.quiz.domain;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class QuizQuestionProgressData {

    private int repetition;

    private int intervalDays;

    private double easinessFactor;

    private ZonedDateTime nextDueDate;

    private ZonedDateTime lastAnsweredAt;

    private double lastScore;

    private List<Attempt> attempts;

    public static class Attempt {

        private ZonedDateTime answered_at;

        private double score;

        // Getters and Setters

        public ZonedDateTime getAnswered_at() {
            return answered_at;
        }

        public void setAnswered_at(ZonedDateTime answered_at) {
            this.answered_at = answered_at;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }
    }

    // Getters and Setters

    public int getRepetition() {
        return repetition;
    }

    public void setRepetition(int repetition) {
        this.repetition = repetition;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(int intervalDays) {
        this.intervalDays = intervalDays;
    }

    public double getEasinessFactor() {
        return easinessFactor;
    }

    public void setEasinessFactor(double easinessFactor) {
        this.easinessFactor = easinessFactor;
    }

    public ZonedDateTime getNextDueDate() {
        return nextDueDate;
    }

    public void setNextDueDate(ZonedDateTime nextDueDate) {
        this.nextDueDate = nextDueDate;
    }

    public ZonedDateTime getLastAnsweredAt() {
        return lastAnsweredAt;
    }

    public void setLastAnsweredAt(ZonedDateTime lastAnsweredAt) {
        this.lastAnsweredAt = lastAnsweredAt;
    }

    public double getLastScore() {
        return lastScore;
    }

    public void setLastScore(double lastScore) {
        this.lastScore = lastScore;
    }

    public List<Attempt> getAttempts() {
        return attempts;
    }

    public void setAttempts(List<Attempt> attempts) {
        this.attempts = attempts;
    }

    public void addAttempt(Attempt attempt) {
        if (this.attempts == null) {
            this.attempts = new ArrayList<>();
        }
        this.attempts.add(attempt);
    }
}
