package de.tum.in.www1.artemis.web.rest.dto.user;

import de.tum.in.www1.artemis.domain.User;

public record UserNameAndLoginDTO(String name, String login) {

    public UserNameAndLoginDTO(User user) {
        this(user.getName(), user.getLogin());
    }
}
