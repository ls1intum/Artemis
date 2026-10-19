package de.tum.cit.aet.artemis.core.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * A minimal DTO exposing only the identifying name information of a {@link User}.
 * This avoids sending the full user entity (with sensitive fields and lazy associations) to the client.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserNameDTO(Long id, String login, String name) implements Serializable {

    /**
     * Converts a User into a UserNameDTO.
     *
     * @param user to convert
     * @return the converted DTO, or null if the user is null
     */
    public static UserNameDTO of(User user) {
        if (user == null) {
            return null;
        }
        return new UserNameDTO(user.getId(), user.getLogin(), user.getName());
    }
}
