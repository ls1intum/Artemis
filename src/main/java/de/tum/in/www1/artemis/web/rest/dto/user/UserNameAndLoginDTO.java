package de.tum.in.www1.artemis.web.rest.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserNameAndLoginDTO(String name, String login) {

    public static UserNameAndLoginDTO of(User user) {
        return new UserNameAndLoginDTO(user.getName(), user.getLogin());
    }
}
