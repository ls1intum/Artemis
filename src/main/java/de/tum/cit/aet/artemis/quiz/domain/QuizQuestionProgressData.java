package de.tum.cit.aet.artemis.quiz.domain;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class QuizQuestionProgressData {

    public QuizQuestionProgressData() {
    };

    private double lastScore;

    private int repetition;

    private double easinessFactor;

    private int interval;

    private int sessionCount;

    private int priority;

    private int box;

    private List<Attempt> attempts;

    public static class Attempt {

        public Attempt() {
        };

        private ZonedDateTime answeredAt;

        private double score;

        // Getters and Setters

        public ZonedDateTime getAnsweredAt() {
            return answeredAt;
        }

        public void setAnsweredAt(ZonedDateTime answeredAt) {
            this.answeredAt = answeredAt;
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

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public double getEasinessFactor() {
        return easinessFactor;
    }

    public void setEasinessFactor(double easinessFactor) {
        this.easinessFactor = easinessFactor;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public void setSessionCount(int sessionCount) {
        this.sessionCount = sessionCount;
    }

    public double getLastScore() {
        return lastScore;
    }

    public void setLastScore(double lastScore) {
        this.lastScore = lastScore;
    }

    public int getBox() {
        return box;
    }

    public void setBox(int box) {
        this.box = box;
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
