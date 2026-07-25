package de.tum.cit.aet.artemis.core.dto.vm;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * View Model object for storing the user's key id, key secret and password.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class KeyIdKeySecretAndPasswordVM {

    private String keyId;

    private String keySecret;

    private String newPassword;

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getKeySecret() {
        return keySecret;
    }

    public void setKeySecret(String keySecret) {
        this.keySecret = keySecret;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
