package de.tum.in.www1.artemis.domain;

import java.util.Objects;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Individual Notification Option which combined make the Notification Settings
 * The unique constraint is needed to avoid duplications.
 * Each user can only set one specific option once.
 */
@Entity
@Table(name = "notification_option", uniqueConstraints = { @UniqueConstraint(columnNames = { "user_id", "option_specifier" }) })
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class NotificationOption extends DomainObject {

    @Column(name = "option_specifier", nullable = false)
    private String optionSpecifier;

    @Column(name = "webapp", columnDefinition = "boolean default true", nullable = false)
    private boolean webapp = true;

    @Column(name = "email", columnDefinition = "boolean default false", nullable = false)
    private boolean email = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("notificationOption")
    private User user;

    public NotificationOption() {
        // Default empty constructor
    }

    public NotificationOption(User user, boolean webapp, boolean email, String optionSpecifier) {
        this.setUser(user);
        this.setWebapp(webapp);
        this.setEmail(email);
        this.setOptionSpecifier(optionSpecifier);
    }

    public String getOptionSpecifier() {
        return optionSpecifier;
    }

    public void setOptionSpecifier(String optionSpecifier) {
        this.optionSpecifier = optionSpecifier;
    }

    public boolean isWebapp() {
        return webapp;
    }

    public void setWebapp(boolean webapp) {
        this.webapp = webapp;
    }

    public boolean isEmail() {
        return email;
    }

    public void setEmail(boolean email) {
        this.email = email;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public String toString() {
        return "NotificationOption{" + ", optionSpecifier='" + optionSpecifier + '\'' + ", webapp=" + webapp + ", email=" + email + ", user=" + user + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(getOptionSpecifier(), getUser(), isWebapp(), isEmail());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        DomainObject domainObject = (DomainObject) object;
        if (domainObject.getId() == null || getId() == null) {
            return false;
        }
        boolean domainObjectCheck = Objects.equals(getId(), domainObject.getId());
        NotificationOption providedOption = (NotificationOption) object;
        boolean userCheck = checkUser(this.user, providedOption.user);
        boolean optionSpecifierCheck = checkOptionSpecifier(this.optionSpecifier, providedOption.optionSpecifier);
        return domainObjectCheck && userCheck && optionSpecifierCheck && this.webapp == providedOption.webapp && this.email == providedOption.email;
    }

    private boolean checkUser(User thisUser, User providedUser) {
        if (thisUser == null && providedUser == null) {
            return true;
        }
        if (thisUser != null && providedUser != null) {
            return thisUser.equals(providedUser);
        }
        return false;
    }

    private boolean checkOptionSpecifier(String thisOptionSpecifier, String providedOptionSpecifier) {
        if (thisOptionSpecifier == null && providedOptionSpecifier == null) {
            return true;
        }
        if (thisOptionSpecifier != null) {
            return thisOptionSpecifier.equals(providedOptionSpecifier);
        }
        return false;
    }
}
