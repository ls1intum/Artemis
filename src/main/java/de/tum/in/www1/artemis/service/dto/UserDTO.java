package de.tum.in.www1.artemis.service.dto;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.*;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;

/**
 * A DTO representing a user, with his authorities.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserDTO extends AuditingEntityDTO {

    private Long id;

    @NotBlank
    @Pattern(regexp = Constants.LOGIN_REGEX)
    @Size(min = USERNAME_MIN_LENGTH, max = USERNAME_MAX_LENGTH)
    private String login;

    @Size(max = 50)
    private String name;

    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;

    @Email
    @Size(min = 5, max = 100)
    private String email;

    private String visibleRegistrationNumber;

    @Size(max = 256)
    private String imageUrl;

    private boolean activated = false;

    @Size(min = 2, max = 6)
    private String langKey;

    private boolean isInternal;

    private ZonedDateTime lastNotificationRead;

    private Set<String> authorities = new HashSet<>();

    private Set<String> groups = new HashSet<>();

    private Set<GuidedTourSetting> guidedTourSettings = new HashSet<>();

    private Set<Organization> organizations;

    private String vcsAccessToken;

    public UserDTO() {
        // Empty constructor needed for Jackson.
    }

    public UserDTO(User user) {
        this(user.getId(), user.getLogin(), user.getName(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getVisibleRegistrationNumber(), user.getActivated(),
                user.getImageUrl(), user.getLangKey(), user.isInternal(), user.getCreatedBy(), user.getCreatedDate(), user.getLastModifiedBy(), user.getLastModifiedDate(),
                user.getLastNotificationRead(), user.getAuthorities(), user.getGroups(), user.getGuidedTourSettings(), user.getOrganizations(), user.getVcsAccessToken());
    }

    public UserDTO(Long id, String login, String name, String firstName, String lastName, String email, String visibleRegistrationNumber, boolean activated, String imageUrl,
            String langKey, boolean isInternal, String createdBy, Instant createdDate, String lastModifiedBy, Instant lastModifiedDate, ZonedDateTime lastNotificationRead,
            Set<Authority> authorities, Set<String> groups, Set<GuidedTourSetting> guidedTourSettings, Set<Organization> organizations, String accessToken) {

        this.id = id;
        this.login = login;
        this.name = name;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.visibleRegistrationNumber = visibleRegistrationNumber;
        this.activated = activated;
        this.imageUrl = imageUrl;
        this.langKey = langKey;
        this.isInternal = isInternal;
        this.setCreatedBy(createdBy);
        this.setCreatedDate(createdDate);
        this.setLastModifiedBy(lastModifiedBy);
        this.setLastModifiedDate(lastModifiedDate);
        this.lastNotificationRead = lastNotificationRead;
        if (authorities != null && Hibernate.isInitialized(authorities)) {
            this.authorities = authorities.stream().map(Authority::getName).collect(Collectors.toSet());
        }
        this.groups = groups;
        this.guidedTourSettings = guidedTourSettings;
        this.organizations = organizations;
        this.vcsAccessToken = accessToken;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getVisibleRegistrationNumber() {
        return visibleRegistrationNumber;
    }

    public void setVisibleRegistrationNumber(String visibleRegistrationNumber) {
        this.visibleRegistrationNumber = visibleRegistrationNumber;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public String getLangKey() {
        return langKey;
    }

    public void setLangKey(String langKey) {
        this.langKey = langKey;
    }

    public ZonedDateTime getLastNotificationRead() {
        return lastNotificationRead;
    }

    public void setLastNotificationRead(ZonedDateTime lastNotificationRead) {
        this.lastNotificationRead = lastNotificationRead;
    }

    public Set<String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(Set<String> authorities) {
        this.authorities = authorities;
    }

    public Set<String> getGroups() {
        return groups;
    }

    public void setGroups(Set<String> groups) {
        this.groups = groups;
    }

    public Set<Organization> getOrganizations() {
        return organizations;
    }

    public void setOrganizations(Set<Organization> organizations) {
        this.organizations = organizations;
    }

    public Set<GuidedTourSetting> getGuidedTourSettings() {
        return guidedTourSettings;
    }

    public void setGuidedTourSettings(Set<GuidedTourSetting> guidedTourSettings) {
        this.guidedTourSettings = guidedTourSettings;
    }

    public String getVcsAccessToken() {
        return vcsAccessToken;
    }

    public void setVcsAccessToken(String vcsAccessToken) {
        this.vcsAccessToken = vcsAccessToken;
    }

    @Override
    public String toString() {
        return "UserDTO{" + "login='" + login + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + ", email='" + email + '\'' + ", imageUrl='"
                + imageUrl + '\'' + ", activated=" + activated + ", langKey='" + langKey + '\'' + ", createdBy=" + getCreatedBy() + ", createdDate=" + getCreatedDate()
                + ", lastModifiedBy='" + getLastModifiedBy() + '\'' + ", lastModifiedDate=" + getLastModifiedDate() + ", lastNotificationRead=" + lastNotificationRead
                + ", authorities=" + authorities + "}";
    }

    public boolean isInternal() {
        return isInternal;
    }

    public void setInternal(boolean internal) {
        isInternal = internal;
    }
}
