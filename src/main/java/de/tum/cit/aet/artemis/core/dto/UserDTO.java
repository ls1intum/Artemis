package de.tum.cit.aet.artemis.core.dto;

import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MIN_LENGTH;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;

/**
 * A DTO representing a user, with their authorities.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserDTO extends AuditingEntityDTO {

    private static final boolean DEFAULT_IS_LOGGED_IN_WITH_PASSKEY = false;

    private static final boolean DEFAULT_IS_SUPER_ADMIN_APPROVED = false;

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

    private List<CourseAccessRightsDTO> courseRoles;

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

    private ZonedDateTime selectedLLMUsageTimestamp;

    private AiSelectionDecision selectedLLMUsage;

    private boolean isLoggedInWithPasskey = DEFAULT_IS_LOGGED_IN_WITH_PASSKEY;

    private boolean isPasskeySuperAdminApproved = DEFAULT_IS_SUPER_ADMIN_APPROVED;

    private boolean memirisEnabled = true;

    public UserDTO() {
        // Empty constructor needed for Jackson.
    }

    public UserDTO(User user) {
        this.id = user.getId();
        this.login = user.getLogin();
        this.name = user.getName();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        this.visibleRegistrationNumber = user.getVisibleRegistrationNumber();
        this.activated = user.getActivated();
        this.imageUrl = user.getImageUrl();
        this.langKey = user.getLangKey();
        this.internal = user.isInternal();
        this.setCreatedBy(user.getCreatedBy());
        this.setCreatedDate(user.getCreatedDate());
        this.setLastModifiedBy(user.getLastModifiedBy());
        this.setLastModifiedDate(user.getLastModifiedDate());
        Set<Authority> authorities = user.getAuthorities();
        if (authorities != null && Hibernate.isInitialized(authorities)) {
            this.authorities = authorities.stream().map(Authority::getName).collect(Collectors.toSet());
        }
        Set<UserCourseRole> userCourseRoles = user.getCourseRoles();
        if (userCourseRoles != null && Hibernate.isInitialized(userCourseRoles)) {
            this.courseRoles = userCourseRoles.stream()
                    .collect(Collectors.groupingBy(ucr -> ucr.getCourse().getId(), Collectors.mapping(UserCourseRole::getRole, Collectors.toSet()))).entrySet().stream()
                    .map(e -> new CourseAccessRightsDTO(e.getKey(), e.getValue())).toList();
        }
        Set<Organization> organizations = user.getOrganizations();
        if (organizations != null && Hibernate.isInitialized(organizations)) {
            this.organizations = organizations;
        }
        this.selectedLLMUsage = user.getSelectedLLMUsage();
        this.selectedLLMUsageTimestamp = user.getSelectedLLMUsageTimestamp();
        this.memirisEnabled = user.isMemirisEnabled();
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

    public List<CourseAccessRightsDTO> getCourseRoles() {
        return courseRoles;
    }

    public void setCourseRoles(List<CourseAccessRightsDTO> courseRoles) {
        this.courseRoles = courseRoles;
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

    public boolean isLoggedInWithPasskey() {
        return isLoggedInWithPasskey;
    }

    public void setLoggedInWithPasskey(boolean isLoggedInWithPasskey) {
        this.isLoggedInWithPasskey = isLoggedInWithPasskey;
    }

    public boolean isPasskeySuperAdminApproved() {
        return isPasskeySuperAdminApproved;
    }

    public void setPasskeySuperAdminApproved(boolean isPasskeySuperAdminApproved) {
        this.isPasskeySuperAdminApproved = isPasskeySuperAdminApproved;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public AiSelectionDecision getSelectedLLMUsage() {
        return selectedLLMUsage;
    }

    public void setSelectedLLMUsage(AiSelectionDecision selectedLLMUsage) {
        this.selectedLLMUsage = selectedLLMUsage;
    }

    public ZonedDateTime getSelectedLLMUsageTimestamp() {
        return selectedLLMUsageTimestamp;
    }

    public void setSelectedLLMUsageTimestamp(ZonedDateTime selectedLLMUsageTimestamp) {
        this.selectedLLMUsageTimestamp = selectedLLMUsageTimestamp;
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
                + ", lastModifiedBy='" + getLastModifiedBy() + '\'' + ", lastModifiedDate=" + getLastModifiedDate() + ", isLoggedInWithPasskey=" + isLoggedInWithPasskey
                + ", authorities=" + authorities + ", isPasskeySuperAdminApproved=" + isPasskeySuperAdminApproved + "}";
    }
}
