package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/** Minimal identity information for an assigned presenter. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentStudentDTO(String login, String name, String firstName, String lastName, String email) {

    public PresentationAssessmentStudentDTO(String login, String firstName, String lastName, String email) {
        this(login, User.displayName(firstName, lastName, login), firstName, lastName, email);
    }

    public static PresentationAssessmentStudentDTO of(User student) {
        return new PresentationAssessmentStudentDTO(student.getLogin(), student.getName(), student.getFirstName(), student.getLastName(), student.getEmail());
    }
}
