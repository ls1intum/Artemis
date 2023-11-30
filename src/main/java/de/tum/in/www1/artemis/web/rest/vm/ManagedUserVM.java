package de.tum.in.www1.artemis.web.rest.vm;

import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.dto.UserDTO;

/**
 * View Model extending the UserDTO, which is meant to be used in the user management UI.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ManagedUserVM extends UserDTO {

    @Size(min = Constants.PASSWORD_MIN_LENGTH, max = Constants.PASSWORD_MAX_LENGTH)
    private String password;

    public ManagedUserVM() {
        // Empty constructor needed for Jackson.
    }

    public ManagedUserVM(User user) {
        super(user);
    }

    public ManagedUserVM(User user, String password) {
        this(user);
        setPassword(password);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "ManagedUserVM{" + "} " + super.toString();
    }
}
