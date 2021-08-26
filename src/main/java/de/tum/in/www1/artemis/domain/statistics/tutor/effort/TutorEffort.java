package de.tum.in.www1.artemis.domain.statistics.tutor.effort;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * A TutorEffort.
 */
@Entity
@Table(name = "tutor_effort")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorEffort extends DomainObject {

    @Column(name = "userId")
    private Long userId;

    @Column(name = "number_submissions_assessed")
    private int numberOfSubmissionsAssessed;

    @Column(name = "total_time_spent_minutes")
    private int totalTimeSpentMinutes;

    @Column(name = "exercise_id")
    private Long exerciseId;

    @Column(name = "course_id")
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
