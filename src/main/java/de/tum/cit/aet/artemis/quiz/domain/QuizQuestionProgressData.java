package de.tum.cit.aet.artemis.quiz.domain;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The class ist used to track the progress of a quiz question for a user.
 * The attributes are based on the SM-2 algorithm and the Leitner system.
 * <a href="https://supermemo.guru/wiki/SuperMemo_1.0_for_DOS_(1987)#Algorithm_SM-2">Resource for a quick lookup</a>
 */
public class QuizQuestionProgressData {

    private double lastScore;

    // The number of times the question has been answered correctly in a row starting from the last answer
    private int repetition;

    // The easiness factor is a value that determines how easy a question is perceived by the user based on the SM-2 algorithm
    private double easinessFactor;

    // The interval is the number of days until the question should be shown again
    private int interval;

    // The number of sessions the question has been answered
    private int sessionCount;

    // The priority is used to determine the order in which questions are shown
    private int priority;

    // The due date is the date when the question should be shown again
    private ZonedDateTime dueDate;

    // The box is used to track the progress of the question based on the Leitner system - correct answers move the question to a higher box, incorrect answers move it to a lower
    // box
    private int box;

    private List<Attempt> attempts;

    public static class Attempt {

        private ZonedDateTime answeredAt;

        private double score;

        public Attempt() {
        }

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

    public QuizQuestionProgressData() {
    }

    // Getters and Setters

    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(ZonedDateTime dueDate) {
        this.dueDate = dueDate;
    }

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
