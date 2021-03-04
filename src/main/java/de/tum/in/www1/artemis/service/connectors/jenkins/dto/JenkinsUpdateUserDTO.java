package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JenkinsUpdateUserDTO {

    private String fullName;

    private String description;

    private final UserProperty2 userProperty2;

    private final UserProperty5 userProperty5;

    private final UserProperty6 userProperty6;

    private final UserProperty8 userProperty8;

    private final UserProperty9 userProperty9;

    private final UserProperty11 userProperty11;

    private final UserProperty12 userProperty12;

    public JenkinsUpdateUserDTO() {
        userProperty2 = new UserProperty2();
        userProperty5 = new UserProperty5();
        userProperty6 = new UserProperty6();
        userProperty8 = new UserProperty8();
        userProperty9 = new UserProperty9();
        userProperty11 = new UserProperty11();
        userProperty12 = new UserProperty12();
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setAddress(String address) {
        userProperty2.setAddress(address);
    }

    public void setPassword(String password) {
        userProperty8.setPassword(password);
        userProperty8.setPassword2(password);
    }

    public UserProperty2 getUserProperty2() {
        return userProperty2;
    }

    public UserProperty5 getUserProperty5() {
        return userProperty5;
    }

    public void setPrimaryViewName(String primaryViewName) {
        userProperty5.setPrimaryViewName(primaryViewName);
    }

    public UserProperty6 getUserProperty6() {
        return userProperty6;
    }

    public UserProperty8 getUserProperty8() {
        return userProperty8;
    }

    public UserProperty9 getUserProperty9() {
        return userProperty9;
    }

    public UserProperty11 getUserProperty11() {
        return userProperty11;
    }

    public UserProperty12 getUserProperty12() {
        return userProperty12;
    }

    public void setProviderId(String providerId) {
        userProperty6.setProviderId(providerId);
    }

    public void setAuthorizedKeys(String authorizedKeys) {
        userProperty9.setAuthorizedKeys(authorizedKeys);
    }

    public void setInsensitiveSearch(boolean insensitiveSearch) {
        userProperty11.setInsensitiveSearch(insensitiveSearch);
    }

    public void setTimeZoneName(String timeZoneName) {
        userProperty12.setTimeZoneName(timeZoneName);
    }
}

class UserProperty2 {

    private String address;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}

class UserProperty5 {

    private String primaryViewName;

    public UserProperty5() {
    }

    public String getPrimaryViewName() {
        return primaryViewName;
    }

    public void setPrimaryViewName(String primaryViewName) {
        this.primaryViewName = primaryViewName;
    }
}

class UserProperty6 {

    private String providerId;

    public UserProperty6() {

    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }
}

class UserProperty8 {

    private String password;

    private String password2;

    @JsonProperty("$redact")
    private List<String> redact;

    public UserProperty8() {
        redact = List.of("password", "password2");
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword2() {
        return password2;
    }

    public void setPassword2(String password2) {
        this.password2 = password2;
    }

    public List<String> getRedact() {
        return redact;
    }

    public void setRedact(List<String> redact) {
        this.redact = redact;
    }
}

class UserProperty9 {

    private String authorizedKeys;

    public UserProperty9() {

    }

    public void setAuthorizedKeys(String authorizedKeys) {
        this.authorizedKeys = authorizedKeys;
    }

    public String getAuthorizedKeys() {
        return authorizedKeys;
    }
}

class UserProperty11 {

    private boolean insensitiveSearch;

    public UserProperty11() {
        insensitiveSearch = false;
    }

    public void setInsensitiveSearch(boolean insensitiveSearch) {
        this.insensitiveSearch = insensitiveSearch;
    }

    public boolean isInsensitiveSearch() {
        return insensitiveSearch;
    }
}

class UserProperty12 {

    private String timeZoneName;

    public UserProperty12() {

    }

    public void setTimeZoneName(String timeZoneName) {
        this.timeZoneName = timeZoneName;
    }

    public String getTimeZoneName() {
        return timeZoneName;
    }
}
