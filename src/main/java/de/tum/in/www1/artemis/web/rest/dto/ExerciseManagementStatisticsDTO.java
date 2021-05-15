package de.tum.in.www1.artemis.web.rest.dto;

public class ExerciseManagementStatisticsDTO {

    private double averageScoreOfExercise;

    private double maxPointsOfExercise;

    private int[] scoreDistribution;

    private int numberOfExerciseScores;

    private long numberOfParticipations;

    private long numberOfStudentsInCourse;

    private long numberOfQuestions;

    private long numberOfAnsweredQuestions;

    public double getAverageScoreOfExercise() {
        return averageScoreOfExercise;
    }

    public void setAverageScoreOfExercise(double averageScoreOfExercise) {
        this.averageScoreOfExercise = averageScoreOfExercise;
    }

    public int[] getScoreDistribution() {
        return scoreDistribution;
    }

    public void setScoreDistribution(int[] scoreDistribution) {
        this.scoreDistribution = scoreDistribution;
    }

    public int getNumberOfExerciseScores() {
        return numberOfExerciseScores;
    }

    public void setNumberOfExerciseScores(int numberOfExerciseScores) {
        this.numberOfExerciseScores = numberOfExerciseScores;
    }

    public long getNumberOfParticipations() {
        return numberOfParticipations;
    }

    public void setNumberOfParticipations(long numberOfParticipations) {
        this.numberOfParticipations = numberOfParticipations;
    }

    public long getNumberOfStudentsInCourse() {
        return numberOfStudentsInCourse;
    }

    public void setNumberOfStudentsInCourse(long numberOfStudentsInCourse) {
        this.numberOfStudentsInCourse = numberOfStudentsInCourse;
    }

    public long getNumberOfQuestions() {
        return numberOfQuestions;
    }

    public void setNumberOfQuestions(long numberOfQuestions) {
        this.numberOfQuestions = numberOfQuestions;
    }

    public long getNumberOfAnsweredQuestions() {
        return numberOfAnsweredQuestions;
    }

    public void setNumberOfAnsweredQuestions(long numberOfAnsweredQuestions) {
        this.numberOfAnsweredQuestions = numberOfAnsweredQuestions;
    }

    public double getMaxPointsOfExercise() {
        return maxPointsOfExercise;
    }

    public void setMaxPointsOfExercise(double maxPointsOfExercise) {
        this.maxPointsOfExercise = maxPointsOfExercise;
    }
}
