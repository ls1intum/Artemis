package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AuthorDTO(Long id, String name, String imageUrl) {

    public AuthorDTO(User user) {
        this(user.getId(), user.getName(), user.getImageUrl());
    }

    /**
     * Return an AuthorDTO for the User
     *
     * @param user the User entity
     * @return the created AuthorDTO
     * @throws BadRequestAlertException if the user does not exist
     */
    public static AuthorDTO fromUser(User user) {
        return user == null ? null : new AuthorDTO(user);
    }
}
