package de.tum.in.www1.artemis.web.rest.dto;

import java.time.LocalDateTime;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

public class TutorialGroupFreePeriodDTO {

    @NotNull
    public LocalDateTime startDate;

    @NotNull
    public LocalDateTime endDate;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String reason;

    public TutorialGroupFreePeriodDTO(LocalDateTime startDate, LocalDateTime endDate, String reason) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
