package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AuthorDTO(Long id, String name, String imageUrl) {

    public AuthorDTO(User user) {
        this(user.getId(), user.getName(), user.getImageUrl());
    }

    public static AuthorDTO fromUser(User user) {
        if (user == null) {
            throw new BadRequestAlertException("User does not exist.", "reaction", "missingUser");
        }
        return new AuthorDTO(user);
    }
}
