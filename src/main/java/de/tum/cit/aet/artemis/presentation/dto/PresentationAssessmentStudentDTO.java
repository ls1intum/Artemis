package de.tum.cit.aet.artemis.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/** Minimal identity information for an assigned presenter. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentStudentDTO(String login, String name, String firstName, String lastName, String email) {

    public static PresentationAssessmentStudentDTO of(User student) {
        return new PresentationAssessmentStudentDTO(student.getLogin(), student.getName(), student.getFirstName(), student.getLastName(), student.getEmail());
    }
}
