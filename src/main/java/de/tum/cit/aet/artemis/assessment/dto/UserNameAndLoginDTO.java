package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserNameAndLoginDTO(String name, String login) {

    /**
     * Creates the DTO from the name parts selected by a query, since the display name is not a column.
     *
     * @param login     the login of the user
     * @param firstName the first name of the user
     * @param lastName  the last name of the user
     */
    public UserNameAndLoginDTO(String login, String firstName, String lastName) {
        this(User.displayName(firstName, lastName, login), login);
    }

    public static UserNameAndLoginDTO of(User user) {
        return new UserNameAndLoginDTO(user.getName(), user.getLogin());
    }
}
