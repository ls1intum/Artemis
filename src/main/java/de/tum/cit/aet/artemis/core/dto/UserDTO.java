package de.tum.cit.aet.artemis.core.dto;

import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MIN_LENGTH;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.User;

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

    private boolean internal;

    private Set<String> authorities = new HashSet<>();

    private Set<String> groups = new HashSet<>();

    private Set<Organization> organizations;

    private String vcsAccessToken;

    private ZonedDateTime vcsAccessTokenExpiryDate;

    /**
     * True if
     * <ul>
     * <li>No passkey has been registered for this user yet</li>
     * <li>and the passkey feature is enabled</li>
     * <li>and <code>artemis.user-management.passkey.ask-users-to-setup</code> is set to true</li>
     * </ul>
     */
    private boolean askToSetupPasskey = false;

    private ZonedDateTime externalLLMUsageAccepted;

    private ZonedDateTime internalLLMUsageAccepted;

    private boolean memirisEnabled = false;

    public UserDTO() {
        // Empty constructor needed for Jackson.
    }

    public UserDTO(User user) {
        this(user.getId(), user.getLogin(), user.getName(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getVisibleRegistrationNumber(), user.getActivated(),
                user.getImageUrl(), user.getLangKey(), user.isInternal(), user.getCreatedBy(), user.getCreatedDate(), user.getLastModifiedBy(), user.getLastModifiedDate(),
                user.getAuthorities(), user.getGroups(), user.getOrganizations(), user.getExternalLLMUsageAcceptedTimestamp(), user.getInternalLLMUsageAcceptedTimestamp(),
                user.isMemirisEnabled());
    }

    public UserDTO(Long id, String login, String name, String firstName, String lastName, String email, String visibleRegistrationNumber, boolean activated, String imageUrl,
            String langKey, boolean internal, String createdBy, Instant createdDate, String lastModifiedBy, Instant lastModifiedDate, Set<Authority> authorities,
            Set<String> groups, Set<Organization> organizations, ZonedDateTime externalLLMUsageAccepted, ZonedDateTime internalLLMUsageAccepted, boolean memirisEnabled) {

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
        this.internal = internal;
        this.setCreatedBy(createdBy);
        this.setCreatedDate(createdDate);
        this.setLastModifiedBy(lastModifiedBy);
        this.setLastModifiedDate(lastModifiedDate);
        if (authorities != null && Hibernate.isInitialized(authorities)) {
            this.authorities = authorities.stream().map(Authority::getName).collect(Collectors.toSet());
        }
        this.groups = groups;
        this.organizations = organizations;
        this.externalLLMUsageAccepted = externalLLMUsageAccepted;
        this.internalLLMUsageAccepted = internalLLMUsageAccepted;
        this.memirisEnabled = memirisEnabled;
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

    public String getVcsAccessToken() {
        return vcsAccessToken;
    }

    /**
     * Only set this token if it is absolutely necessary in the client, otherwise this might reveal secret information
     *
     * @param vcsAccessToken the access token for the VCS repository
     */
    public void setVcsAccessToken(String vcsAccessToken) {
        this.vcsAccessToken = vcsAccessToken;
    }

    public void setVcsAccessTokenExpiryDate(ZonedDateTime zoneDateTime) {
        this.vcsAccessTokenExpiryDate = zoneDateTime;
    }

    public ZonedDateTime getVcsAccessTokenExpiryDate() {
        return vcsAccessTokenExpiryDate;
    }

    public void setAskToSetupPasskey(boolean askToSetupPasskey) {
        this.askToSetupPasskey = askToSetupPasskey;
    }

    public boolean getAskToSetupPasskey() {
        return askToSetupPasskey;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public ZonedDateTime getExternalLLMUsageAccepted() {
        return externalLLMUsageAccepted;
    }

    public void setExternalLLMUsageAccepted(ZonedDateTime externalLLMUsageAccepted) {
        this.externalLLMUsageAccepted = externalLLMUsageAccepted;
    }

    public ZonedDateTime getInternalLLMUsageAccepted() {
        return internalLLMUsageAccepted;
    }

    public void setInternalLLMUsageAccepted(ZonedDateTime internalLLMUsageAccepted) {
        this.internalLLMUsageAccepted = internalLLMUsageAccepted;
    }

    public boolean isMemirisEnabled() {
        return memirisEnabled;
    }

    public void setMemirisEnabled(boolean memirisEnabled) {
        this.memirisEnabled = memirisEnabled;
    }

    @Override
    public String toString() {
        return "UserDTO{" + "login='" + login + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + ", email='" + email + '\'' + ", imageUrl='"
                + imageUrl + '\'' + ", activated=" + activated + ", langKey='" + langKey + '\'' + ", createdBy=" + getCreatedBy() + ", createdDate=" + getCreatedDate()
                + ", lastModifiedBy='" + getLastModifiedBy() + '\'' + ", lastModifiedDate=" + getLastModifiedDate() + ", authorities=" + authorities + "}";
    }
}
