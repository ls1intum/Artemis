package de.tum.cit.aet.artemis.domain.statistics.tutor.effort;

/**
 * A data entry used by the tutor effort statistics page. It represents the respective information in terms of
 * number of submissions assessed as well as time spent for each tutor in a particular exercise.
 */
public class TutorEffort {

    private Long userId;

    private int numberOfSubmissionsAssessed;

    private double totalTimeSpentMinutes;

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

    public double getTotalTimeSpentMinutes() {
        return totalTimeSpentMinutes;
    }

    public void setTotalTimeSpentMinutes(double totalTimeSpentMinutes) {
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
