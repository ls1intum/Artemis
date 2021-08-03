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

    private ZonedDateTime lastNotificationRead;

    private Set<String> authorities = new HashSet<>();

    private Set<String> groups = new HashSet<>();

    private Set<GuidedTourSetting> guidedTourSettings = new HashSet<>();

    private Set<Organization> organizations;

    public UserDTO() {
        // Empty constructor needed for Jackson.
    }

    public UserDTO(User user) {
        new Builder().createdBy(user.getCreatedBy()).createdDate(user.getCreatedDate()).lastModifiedBy(user.getLastModifiedBy()).lastModifiedDate(user.getLastModifiedDate())
                .id(user.getId()).login(user.getLogin()).name(user.getName()).firstName(user.getFirstName()).lastName(user.getLastName()).email(user.getEmail())
                .visibleRegistrationNumber(user.getVisibleRegistrationNumber()).imageUrl(user.getImageUrl()).activated(user.getActivated()).langKey(user.getLangKey())
                .lastNotificationRead(user.getLastNotificationRead()).authorities(user.getAuthorities()).groups(user.getGroups()).guidedTourSettings(user.getGuidedTourSettings())
                .organizations(user.getOrganizations()).build();
    }

    // Builder Constructor
    public UserDTO(Builder builder) {
        super(builder);
        this.id = builder.id;
        this.login = builder.login;
        this.name = builder.name;
        this.firstName = builder.firstName;
        this.lastName = builder.lastName;
        this.email = builder.email;
        this.visibleRegistrationNumber = builder.visibleRegistrationNumber;
        this.activated = builder.activated;
        this.imageUrl = builder.imageUrl;
        this.langKey = builder.langKey;
        this.lastNotificationRead = builder.lastNotificationRead;
        if (builder.authorities != null && Hibernate.isInitialized(builder.authorities)) {
            this.authorities = builder.authorities.stream().map(Authority::getName).collect(Collectors.toSet());
        }
        this.groups = builder.groups;
        this.guidedTourSettings = builder.guidedTourSettings;
        this.organizations = builder.organizations;
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

    @Override
    public String toString() {
        return "UserDTO{" + "login='" + login + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + ", email='" + email + '\'' + ", imageUrl='"
                + imageUrl + '\'' + ", activated=" + activated + ", langKey='" + langKey + '\'' + ", createdBy=" + getCreatedBy() + ", createdDate=" + getCreatedDate()
                + ", lastModifiedBy='" + getLastModifiedBy() + '\'' + ", lastModifiedDate=" + getLastModifiedDate() + ", lastNotificationRead=" + lastNotificationRead
                + ", authorities=" + authorities + "}";
    }

    // Builder class for UserDTO
    public static class Builder extends AuditingEntityDTO.Builder {

        private Long id;

        private String login;

        private String name;

        private String firstName;

        private String lastName;

        private String email;

        private String visibleRegistrationNumber;

        private String imageUrl;

        private boolean activated = false;

        private String langKey;

        private ZonedDateTime lastNotificationRead;

        private Set<Authority> authorities = new HashSet<>();

        private Set<String> groups = new HashSet<>();

        private Set<GuidedTourSetting> guidedTourSettings = new HashSet<>();

        private Set<Organization> organizations;

        public Builder createdBy(String createdBy) {
            super.createdBy = createdBy;
            return this;
        }

        public Builder createdDate(Instant createdDate) {
            super.createdDate = createdDate;
            return this;
        }

        public Builder lastModifiedBy(String lastModifiedBy) {
            super.lastModifiedBy = lastModifiedBy;
            return this;
        }

        public Builder lastModifiedDate(Instant lastModifiedDate) {
            super.lastModifiedDate = lastModifiedDate;
            return this;
        }

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder login(String login) {
            this.login = login;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder visibleRegistrationNumber(String visibleRegistrationNumber) {
            this.visibleRegistrationNumber = visibleRegistrationNumber;
            return this;
        }

        public Builder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public Builder activated(boolean activated) {
            this.activated = activated;
            return this;
        }

        public Builder langKey(String langKey) {
            this.langKey = langKey;
            return this;
        }

        public Builder lastNotificationRead(ZonedDateTime lastNotificationRead) {
            this.lastNotificationRead = lastNotificationRead;
            return this;
        }

        public Builder authorities(Set<Authority> authorities) {
            this.authorities = authorities;
            return this;
        }

        public Builder groups(Set<String> groups) {
            this.groups = groups;
            return this;
        }

        public Builder guidedTourSettings(Set<GuidedTourSetting> guidedTourSettings) {
            this.guidedTourSettings = guidedTourSettings;
            return this;
        }

        public Builder organizations(Set<Organization> organizations) {
            this.organizations = organizations;
            return this;
        }

        public UserDTO build() {
            return new UserDTO(this);
        }
    }
}
