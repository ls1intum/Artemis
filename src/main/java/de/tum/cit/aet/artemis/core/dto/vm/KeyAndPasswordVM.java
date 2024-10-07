package de.tum.cit.aet.artemis.core.dto.vm;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * View Model object for storing the user's key and password.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class KeyAndPasswordVM {

    private String key;

    private String newPassword;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
