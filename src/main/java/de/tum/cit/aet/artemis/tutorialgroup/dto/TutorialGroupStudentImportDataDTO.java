package de.tum.cit.aet.artemis.tutorialgroup.dto;

import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupStudentImportDataDTO(@Size(max = 50) String login, @Size(max = 10) String registrationNumber) {
}
