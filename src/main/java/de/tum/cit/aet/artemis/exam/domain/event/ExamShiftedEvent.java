package de.tum.cit.aet.artemis.exam.domain.event;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import de.tum.cit.aet.artemis.exam.dto.examevent.ExamShiftedEventDTO;

/**
 * An event indicating that exam start and end dates were shifted for a specific student exam. In case of this event working time doesn't change.
 */
@Entity
@DiscriminatorValue(value = "S")
public class ExamShiftedEvent extends ExamLiveEvent {

    /**
     * The new start date
     */
    @Column(name = "newStartDate")
    private Instant newStartDate;

    /**
     * The new end date
     */
    @Column(name = "newEndDate")
    private Instant newEndDate;

    /**
     * While the event always contains a new start and end date for a specific student exam,
     * this flags indicates whether the dates were updated for every student exam of the exam.
     */
    @Column(name = "courseWide")
    private boolean courseWide;

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

    public boolean isCourseWide() {
        return courseWide;
    }

    public void setCourseWide(boolean courseWide) {
        this.courseWide = courseWide;
    }

    @Override
    public ExamShiftedEventDTO asDTO() {
        return new ExamShiftedEventDTO(this.getId(), this.getCreatedBy(), this.getCreatedDate(), newStartDate, newEndDate, courseWide);
    }
}
