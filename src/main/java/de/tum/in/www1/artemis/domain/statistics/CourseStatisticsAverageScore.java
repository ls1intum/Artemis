package de.tum.in.www1.artemis.domain.statistics;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseStatisticsAverageScore {

    private final long exerciseId;

    private final String exerciseName;

    private final ZonedDateTime releaseDate;

    private double averageScore;

    private ExerciseType exerciseType;

    public CourseStatisticsAverageScore(long exerciseId, String exerciseName, ZonedDateTime releaseDate, double averageScore) {
        this.exerciseId = exerciseId;
        this.exerciseName = exerciseName;
        this.releaseDate = releaseDate;
        this.averageScore = averageScore;
    }

    public CourseStatisticsAverageScore() {
        this.exerciseId = 0L;
        this.exerciseName = "";
        this.releaseDate = null;
        this.averageScore = 0.0;
        this.exerciseType = null;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public double getAverageScore() {
        return averageScore;
    }

    public ExerciseType getExerciseType() {
        return exerciseType;
    }

    public void setAverageScore(double averageScore) {
        this.averageScore = averageScore;
    }

    public void setExerciseType(ExerciseType exerciseType) {
        this.exerciseType = exerciseType;
    }
}
