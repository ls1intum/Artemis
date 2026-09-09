package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentDTO(long id, String login, String firstName, String lastName, String name, String registrationNumber,   // null if not provided
        String email) {

    public StudentDTO(long id, String login, String firstName, String lastName, String registrationNumber, String email) {
        this(id, login, firstName, lastName, User.displayName(firstName, lastName, login), registrationNumber, email);
    }
}
