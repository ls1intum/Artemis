package de.tum.cit.aet.artemis.exam.dto;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.ExamUser;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExportExamUserDTO(String matriculationNumber, @NotBlank String login, String name, String email, String room, String seat, String fullLocation) {

    public ExportExamUserDTO(ExamUser examUser) {
        this(examUser, null);
    }

    public ExportExamUserDTO(ExamUser examUser, String fullLocation) {
        this(examUser.getUser().getRegistrationNumber(), examUser.getUser().getLogin(), examUser.getUser().getName(), examUser.getUser().getEmail(), examUser.getPlannedRoom(),
                examUser.getPlannedSeat(), fullLocation);
    }
}
