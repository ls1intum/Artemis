package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

public class ExamInformationDTO {

    public ExamInformationDTO() {
    }

    public ExamInformationDTO(ZonedDateTime latestIndividualEndDate) {
        this.latestIndividualEndDate = latestIndividualEndDate;
    }

    private ZonedDateTime latestIndividualEndDate;

    public ZonedDateTime getLatestIndividualEndDate() {
        return latestIndividualEndDate;
    }

    public void setLatestIndividualEndDate(ZonedDateTime latestIndividualEndDate) {
        this.latestIndividualEndDate = latestIndividualEndDate;
    }
}
