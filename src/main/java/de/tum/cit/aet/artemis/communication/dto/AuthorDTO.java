package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AuthorDTO(Long id, String name, String imageUrl) {

    public AuthorDTO(User user) {
        this(user.getId(), user.getName(), user.getImageUrl());
    }
}
