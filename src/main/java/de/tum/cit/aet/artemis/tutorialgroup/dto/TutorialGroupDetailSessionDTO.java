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

    @NotNull
    private TutorialGroupDetailSessionDTOStatus status;

    private final Integer attendanceCount;

    @NotNull
    @JsonIgnore
    private final TutorialGroupSessionStatus originSessionStatus;

    public TutorialGroupDetailSessionDTO(ZonedDateTime start, ZonedDateTime end, String location, TutorialGroupSessionStatus originSessionStatus, Integer attendanceCount) {
        this.start = start;
        this.end = end;
        this.location = location;
        this.originSessionStatus = originSessionStatus;
        this.status = TutorialGroupDetailSessionDTOStatus.ACTIVE;
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

    public TutorialGroupSessionStatus getOriginSessionStatus() {
        return originSessionStatus;
    }

    public TutorialGroupDetailSessionDTOStatus getStatus() {
        return status;
    }

    public void setStatus(TutorialGroupDetailSessionDTOStatus status) {
        this.status = status;
    }

    public Integer getAttendanceCount() {
        return attendanceCount;
    }
}
