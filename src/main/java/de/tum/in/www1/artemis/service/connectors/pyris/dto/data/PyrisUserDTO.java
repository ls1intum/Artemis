package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import de.tum.in.www1.artemis.domain.User;

public record PyrisUserDTO(long id, String firstName, String lastName) {

    public PyrisUserDTO(User user) {
        this(user.getId(), user.getFirstName(), user.getLastName());
    }
}
