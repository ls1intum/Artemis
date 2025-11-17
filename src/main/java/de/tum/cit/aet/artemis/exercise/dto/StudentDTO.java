package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentDTO(long id, String login, String firstName, String lastName, String name, String registrationNumber,   // null if not provided
        String email) {

    public StudentDTO(long id, String login, String firstName, String lastName, String registrationNumber, String email) {
        this(id, login, firstName, lastName, createName(firstName, lastName), registrationNumber, email);
    }

    private static String createName(@Nullable String firstName, @Nullable String lastName) {
        boolean hasFirst = firstName != null && !firstName.isEmpty();
        boolean hasLast = lastName != null && !lastName.isEmpty();

        if (hasFirst && hasLast) {
            return firstName + " " + lastName;
        }
        else if (hasFirst) {
            return firstName;
        }
        else if (hasLast) {
            return lastName;
        }
        else {
            return "";
        }
    }
}
