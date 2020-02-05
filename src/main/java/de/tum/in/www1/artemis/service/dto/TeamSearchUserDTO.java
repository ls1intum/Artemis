package de.tum.in.www1.artemis.service.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.User;

/**
 * A DTO representing a user returned by searching for a student to add to a team.
 */
public class TeamSearchUserDTO {

    private Long id;

    @NotBlank
    @Pattern(regexp = Constants.LOGIN_REGEX)
    @Size(min = 1, max = 50)
    private String login;

    @Size(max = 50)
    private String name;

    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;

    private boolean isAssignedToTeam;

    public TeamSearchUserDTO() {
        // Empty constructor needed for Jackson.
    }

    public TeamSearchUserDTO(User user) {
        this(user.getId(), user.getLogin(), user.getName(), user.getFirstName(), user.getLastName());
    }

    public TeamSearchUserDTO(Long id, String login, String name, String firstName, String lastName) {
        this.id = id;
        this.login = login;
        this.name = name;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public boolean isAssignedToTeam() {
        return isAssignedToTeam;
    }

    public void setIsAssignedToTeam(boolean isAssignedToTeam) {
        this.isAssignedToTeam = isAssignedToTeam;
    }

    @Override
    public String toString() {
        return "TeamSearchUserDTO{" + "login='" + login + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + ", isAssignedToTeam='" + isAssignedToTeam
                + "}";
    }
}
