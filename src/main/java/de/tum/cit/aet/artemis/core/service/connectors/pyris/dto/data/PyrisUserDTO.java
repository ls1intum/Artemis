package de.tum.cit.aet.artemis.core.service.connectors.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisUserDTO(long id, String firstName, String lastName) {

    public PyrisUserDTO(User user) {
        this(user.getId(), user.getFirstName(), user.getLastName());
    }
}
