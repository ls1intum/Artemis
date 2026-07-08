package de.tum.cit.aet.artemis.exam.domain.event;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import de.tum.cit.aet.artemis.exam.dto.examevent.WorkingTimeUpdateEventDTO;

/**
 * An event indicating an updated working time for a specific student exam.
 */
@Entity
@DiscriminatorValue(value = "W")
public class WorkingTimeUpdateEvent extends ExamLiveEvent {

    /**
     * The new working time in seconds.
     */
    @Column(name = "newWorkingTime")
    private int newWorkingTime;

    /**
     * The old working time in seconds.
     */
    @Column(name = "oldWorkingTime")
    private int oldWorkingTime;

    /**
     * While the event always contains the working time for a specific student exam,
     * this flags indicates whether the working time was updated for every student exam of the exam.
     */
    @Column(name = "courseWide")
    private boolean courseWide;

    /**
     * The new start date of the exam. Included on every working time update so a conducting student can update the
     * pre-start countdown and the start-based visibility when the exam schedule changes.
     */
    @Column(name = "newStartDate")
    private Instant newStartDate;

    /**
     * The new end date of the exam. Included on every working time update alongside {@link #newStartDate}.
     */
    @Column(name = "newEndDate")
    private Instant newEndDate;

    public int getNewWorkingTime() {
        return newWorkingTime;
    }

    public void setNewWorkingTime(int newWorkingTime) {
        this.newWorkingTime = newWorkingTime;
    }

    public int getOldWorkingTime() {
        return oldWorkingTime;
    }

    public void setOldWorkingTime(int oldWorkingTime) {
        this.oldWorkingTime = oldWorkingTime;
    }

    public boolean isCourseWide() {
        return courseWide;
    }

    public void setCourseWide(boolean courseWide) {
        this.courseWide = courseWide;
    }

    public Instant getNewStartDate() {
        return newStartDate;
    }

    public void setNewStartDate(Instant newStartDate) {
        this.newStartDate = newStartDate;
    }

    public Instant getNewEndDate() {
        return newEndDate;
    }

    public void setNewEndDate(Instant newEndDate) {
        this.newEndDate = newEndDate;
    }

    @Override
    public WorkingTimeUpdateEventDTO asDTO() {
        return new WorkingTimeUpdateEventDTO(this.getId(), this.getCreatedDate(), newWorkingTime, oldWorkingTime, courseWide, newStartDate, newEndDate);
    }
}
