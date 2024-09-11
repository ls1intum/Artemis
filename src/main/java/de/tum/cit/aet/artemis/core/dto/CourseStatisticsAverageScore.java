package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
// TODO: convert to record
public class CourseStatisticsAverageScore {

    private final long exerciseId;

    private final String exerciseName;

    private final ZonedDateTime releaseDate;

    private double averageScore;

    private ExerciseType exerciseType;

    private Set<String> categories;

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

    public Set<String> getCategories() {
        return categories;
    }

    public void setAverageScore(double averageScore) {
        this.averageScore = averageScore;
    }

    public void setExerciseType(ExerciseType exerciseType) {
        this.exerciseType = exerciseType;
    }

    public void setCategories(Set<String> categories) {
        this.categories = categories;
    }
}
