package de.tum.cit.aet.artemis.exercise.dto;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentDTO(long id, String login, String firstName, String lastName, String name, String registrationNumber,   // null if not provided
        String email) {

    public StudentDTO(long id, String login, String firstName, String lastName, String registrationNumber, String email) {
        this(id, login, firstName, lastName, createName(firstName, lastName), registrationNumber, email);
    }

    private static String createName(@Nullable String firstName, @Nullable String lastName) {
        if (firstName != null && !firstName.isEmpty() && lastName != null && !lastName.isEmpty()) {
            return firstName + " " + lastName;
        }
        else if (firstName != null && !firstName.isEmpty()) {
            return firstName;
        }
        else if (lastName != null && !lastName.isEmpty()) {
            return lastName;
        }
        else {
            return "";
        }
    }
}
