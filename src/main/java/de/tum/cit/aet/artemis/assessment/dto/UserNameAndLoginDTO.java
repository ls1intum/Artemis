package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserNameAndLoginDTO(String name, String login) {

    public static UserNameAndLoginDTO of(User user) {
        return new UserNameAndLoginDTO(user.getName(), user.getLogin());
    }
}
