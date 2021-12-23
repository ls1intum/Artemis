package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.persistence.*;
import javax.validation.constraints.*;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.participation.Participant;

/**
 * A user.
 */
@Entity
@Table(name = "jhi_user")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class User extends AbstractAuditingEntity implements Participant {

    @NotNull
    @Pattern(regexp = Constants.LOGIN_REGEX)
    @Size(min = USERNAME_MIN_LENGTH, max = USERNAME_MAX_LENGTH)
    @Column(length = USERNAME_MAX_LENGTH, unique = true, nullable = false)
    private String login;

    @JsonIgnore
    @Column(name = "password_hash")
    private String password;

    @Size(max = 50)
    @Column(name = "first_name", length = 50)
    private String firstName;

    @Size(max = 50)
    @Column(name = "last_name", length = 50)
    private String lastName;

    @Size(max = 20)
    @Column(name = "registration_number", length = 20)
    @JsonIgnore
    private String registrationNumber;

    // this value is typically null, except the registration number should be explicitly shown in the client
    // currently this is only the case for the course scores page and its csv export, and also for the individual student exam detail
    @Transient
    private String visibleRegistrationNumberTransient = null;

    @Email
    @Size(max = 100)
    @Column(length = 100)
    private String email;

    @NotNull
    @Column(nullable = false)
    private boolean activated = false;

    @Size(min = 2, max = 6)
    @Column(name = "lang_key", length = 6)
    private String langKey;

    @Size(max = 256)
    @Column(name = "image_url", length = 256)
    private String imageUrl;

    @Size(max = 20)
    @Column(name = "activation_key", length = 20)
    @JsonIgnore
    private String activationKey;

    @Size(max = 20)
    @Column(name = "reset_key", length = 20)
    @JsonIgnore
    private String resetKey;

    @Column(name = "reset_date")
    private Instant resetDate = null;

    @Column(name = "last_notification_read")
    private ZonedDateTime lastNotificationRead = null;

    // hides all notifications with a notification date until (before) the given date in the notification sidebar.
    // if the value is null all notifications are displayed
    @Nullable
    @Column(name = "hide_notifications_until")
    private ZonedDateTime hideNotificationsUntil = null;

    /**
     * Word "GROUPS" is being added as a restricted word starting in MySQL 8.0.2
     * Workaround: Annotation @Column(name = "`groups`") escapes this word using backticks.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_groups", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "`groups`")
    private Set<String> groups = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<GuidedTourSetting> guidedTourSettings = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "jhi_user_authority", joinColumns = { @JoinColumn(name = "user_id", referencedColumnName = "id") }, inverseJoinColumns = {
            @JoinColumn(name = "authority_name", referencedColumnName = "name") })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @BatchSize(size = 20)
    private Set<Authority> authorities = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "user_organization", joinColumns = { @JoinColumn(name = "user_id", referencedColumnName = "id") }, inverseJoinColumns = {
            @JoinColumn(name = "organization_id", referencedColumnName = "id") })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("user")
    private Set<Organization> organizations = new HashSet<>();

    public String getLogin() {
        return login;
    }

    // Lowercase the login before saving it in database
    public void setLogin(String login) {
        this.login = StringUtils.lowerCase(login, Locale.ENGLISH);
    }

    public String getParticipantIdentifier() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    /**
     * @return name as a concatenation of first name and last name
     */
    public String getName() {
        if (lastName != null && !lastName.equals("")) {
            return firstName + " " + lastName;
        }
        else {
            return firstName;
        }
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean getActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public String getActivationKey() {
        return activationKey;
    }

    public void setActivationKey(String activationKey) {
        this.activationKey = activationKey;
    }

    public String getResetKey() {
        return resetKey;
    }

    public void setResetKey(String resetKey) {
        this.resetKey = resetKey;
    }

    public Instant getResetDate() {
        return resetDate;
    }

    public void setResetDate(Instant resetDate) {
        this.resetDate = resetDate;
    }

    public ZonedDateTime getLastNotificationRead() {
        return lastNotificationRead;
    }

    public void setLastNotificationRead(ZonedDateTime lastNotificationRead) {
        this.lastNotificationRead = lastNotificationRead;
    }

    public ZonedDateTime getHideNotificationsUntil() {
        return hideNotificationsUntil;
    }

    public void setHideNotificationsUntil(ZonedDateTime hideNotificationsUntil) {
        this.hideNotificationsUntil = hideNotificationsUntil;
    }

    public String getLangKey() {
        return langKey;
    }

    public void setLangKey(String langKey) {
        this.langKey = langKey;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getVisibleRegistrationNumber() {
        return visibleRegistrationNumberTransient;
    }

    public void setVisibleRegistrationNumber(String visibleRegistrationNumber) {
        this.visibleRegistrationNumberTransient = visibleRegistrationNumber;
    }

    public void setVisibleRegistrationNumber() {
        this.visibleRegistrationNumberTransient = this.getRegistrationNumber();
    }

    public Set<String> getGroups() {
        return groups;
    }

    public void setGroups(Set<String> groups) {
        this.groups = groups;
    }

    public Set<Authority> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(Set<Authority> authorities) {
        this.authorities = authorities;
    }

    public Set<Organization> getOrganizations() {
        return organizations;
    }

    public void setOrganizations(Set<Organization> organizations) {
        this.organizations = organizations;
    }

    public Set<GuidedTourSetting> getGuidedTourSettings() {
        return this.guidedTourSettings;
    }

    public void addGuidedTourSetting(GuidedTourSetting setting) {
        this.guidedTourSettings.add(setting);
        setting.setUser(this);
    }

    public void removeGuidedTourSetting(GuidedTourSetting setting) {
        this.guidedTourSettings.remove(setting);
        setting.setUser(null);
    }

    public void setGuidedTourSettings(Set<GuidedTourSetting> guidedTourSettings) {
        this.guidedTourSettings = guidedTourSettings;
    }

    @Override
    @JsonIgnore
    public Set<User> getParticipants() {
        return Set.of(this);
    }

    @JsonIgnore
    public List<GrantedAuthority> getGrantedAuthorities() {
        return getAuthorities().stream().map(authority -> new SimpleGrantedAuthority(authority.getName())).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "User{" + "login='" + login + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + ", email='" + email + '\'' + ", imageUrl='" + imageUrl
                + '\'' + ", activated='" + activated + '\'' + ", langKey='" + langKey + '\'' + ", activationKey='" + activationKey + '\'' + "}";
    }

    /**
     * copy the basic user settings to hide not needed information before sending the user object to the client
     * @return the basic user
     */
    public User copyBasicUser() {
        User user = new User();
        user.setId(getId());
        user.setFirstName(getFirstName());
        user.setLastName(getLastName());
        user.setLogin(getLogin());
        return user;
    }
}
