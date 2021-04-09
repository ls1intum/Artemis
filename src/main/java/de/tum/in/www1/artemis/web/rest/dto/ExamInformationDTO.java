package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamInformationDTO {

    private ZonedDateTime latestIndividualEndDate;

    public ExamInformationDTO() {
    }

    public ExamInformationDTO(ZonedDateTime latestIndividualEndDate) {
        this.latestIndividualEndDate = latestIndividualEndDate;
    }

    public ZonedDateTime getLatestIndividualEndDate() {
        return latestIndividualEndDate;
    }

    public void setLatestIndividualEndDate(ZonedDateTime latestIndividualEndDate) {
        this.latestIndividualEndDate = latestIndividualEndDate;
    }
}
