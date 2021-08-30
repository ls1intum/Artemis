package de.tum.in.www1.artemis.domain.statistics.tutor.effort;

/**
 * A TutorEffort.
 */
public class TutorEffort {

    private Long userId;

    private int numberOfSubmissionsAssessed;

    private int totalTimeSpentMinutes;

    private Long exerciseId;

    private Long courseId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public int getNumberOfSubmissionsAssessed() {
        return numberOfSubmissionsAssessed;
    }

    public void setNumberOfSubmissionsAssessed(int numberOfSubmissionsAssessed) {
        this.numberOfSubmissionsAssessed = numberOfSubmissionsAssessed;
    }

    public int getTotalTimeSpentMinutes() {
        return totalTimeSpentMinutes;
    }

    public void setTotalTimeSpentMinutes(int totalTimeSpentMinutes) {
        this.totalTimeSpentMinutes = totalTimeSpentMinutes;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
}
