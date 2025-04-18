package de.tum.cit.aet.artemis.core.repository.webauthn;

import java.io.Serializable;
import java.util.List;

public class PublicKeyCredentialCreationOptionsDTO implements Serializable {

    private String rpName;

    private String userId;

    private String userName;

    private String userDisplayName;

    private String challenge;

    private List<String> pubKeyCredParams;

    // Getters and setters
    public String getRpName() {
        return rpName;
    }

    public void setRpName(String rpName) {
        this.rpName = rpName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserDisplayName() {
        return userDisplayName;
    }

    public void setUserDisplayName(String userDisplayName) {
        this.userDisplayName = userDisplayName;
    }

    public String getChallenge() {
        return challenge;
    }

    public void setChallenge(String challenge) {
        this.challenge = challenge;
    }

    public List<String> getPubKeyCredParams() {
        return pubKeyCredParams;
    }

    public void setPubKeyCredParams(List<String> pubKeyCredParams) {
        this.pubKeyCredParams = pubKeyCredParams;
    }
}
