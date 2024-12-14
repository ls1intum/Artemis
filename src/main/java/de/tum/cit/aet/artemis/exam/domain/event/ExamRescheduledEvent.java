package de.tum.cit.aet.artemis.exam.domain.event;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import de.tum.cit.aet.artemis.exam.dto.examevent.ExamRescheduledEventDTO;

/**
 * An event indicating that exam start and end dates were rescheduled for a specific student exam. In case of this event working time doesn't change.
 */
@Entity
@DiscriminatorValue(value = "R")
public class ExamRescheduledEvent extends ExamLiveEvent {

    /**
     * The new start date
     */
    @Column(name = "newStartDate")
    private ZonedDateTime newStartDate;

    /**
     * The new end date
     */
    @Column(name = "newEndDate")
    private ZonedDateTime newEndDate;

    public ZonedDateTime getNewStartDate() {
        return newStartDate;
    }

    public void setNewStartDate(ZonedDateTime newStartDate) {
        this.newStartDate = newStartDate;
    }

    public ZonedDateTime getNewEndDate() {
        return newEndDate;
    }

    public void setNewEndDate(ZonedDateTime newEndDate) {
        this.newEndDate = newEndDate;
    }

    @Override
    public ExamRescheduledEventDTO asDTO() {
        return new ExamRescheduledEventDTO(this.getId(), this.getCreatedBy(), this.getCreatedDate(), newStartDate, newEndDate);
    }
}
