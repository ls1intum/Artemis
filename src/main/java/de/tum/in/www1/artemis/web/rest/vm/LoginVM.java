package de.tum.in.www1.artemis.web.rest.vm;

import static de.tum.in.www1.artemis.config.Constants.PASSWORD_MAX_LENGTH;
import static de.tum.in.www1.artemis.config.Constants.PASSWORD_MIN_LENGTH;
import static de.tum.in.www1.artemis.config.Constants.USERNAME_MAX_LENGTH;
import static de.tum.in.www1.artemis.config.Constants.USERNAME_MIN_LENGTH;

import jakarta.validation.constraints.Size;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * View Model object for storing a user's credentials.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LoginVM {

    @NonNull
    @Size(min = USERNAME_MIN_LENGTH, max = USERNAME_MAX_LENGTH)
    private String username;

    @NonNull
    @Size(min = PASSWORD_MIN_LENGTH, max = PASSWORD_MAX_LENGTH)
    private String password;

    private Boolean rememberMe;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean isRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(Boolean rememberMe) {
        this.rememberMe = rememberMe;
    }

    @Override
    public String toString() {
        return "LoginVM{" + "username='" + username + '\'' + ", rememberMe=" + rememberMe + '}';
    }
}
