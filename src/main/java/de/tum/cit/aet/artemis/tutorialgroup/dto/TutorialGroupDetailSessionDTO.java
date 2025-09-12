package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorialGroupDetailSessionDTO {

    @NotNull
    private final ZonedDateTime start;

    @NotNull
    private final ZonedDateTime end;

    @NotNull
    private final String location;

    private boolean isCancelled;

    private boolean locationChanged;

    private boolean timeChanged;

    private boolean dateChanged;

    private final Integer attendanceCount;

    @NotNull
    private final TutorialGroupSessionStatus originSessionStatus;

    public TutorialGroupDetailSessionDTO(ZonedDateTime start, ZonedDateTime end, String location, TutorialGroupSessionStatus originSessionStatus, Integer attendanceCount) {
        this.start = start;
        this.end = end;
        this.location = location;
        this.originSessionStatus = originSessionStatus;
        this.isCancelled = false;
        this.locationChanged = false;
        this.timeChanged = false;
        this.dateChanged = false;
        this.attendanceCount = attendanceCount;
    }

    public ZonedDateTime getStart() {
        return start;
    }

    public ZonedDateTime getEnd() {
        return end;
    }

    public String getLocation() {
        return location;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public void setCancelled(boolean cancelled) {
        isCancelled = cancelled;
    }

    public boolean isLocationChanged() {
        return locationChanged;
    }

    public void setLocationChanged(boolean locationChanged) {
        this.locationChanged = locationChanged;
    }

    public boolean isTimeChanged() {
        return timeChanged;
    }

    public void setTimeChanged(boolean timeChanged) {
        this.timeChanged = timeChanged;
    }

    public boolean isDateChanged() {
        return dateChanged;
    }

    public void setDateChanged(boolean dateChanged) {
        this.dateChanged = dateChanged;
    }

    public Integer getAttendanceCount() {
        return attendanceCount;
    }

    @JsonIgnore
    public TutorialGroupSessionStatus getOriginSessionStatus() {
        return originSessionStatus;
    }
}
