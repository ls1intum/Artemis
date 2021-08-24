package de.tum.in.www1.artemis.domain;

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

    @Id
    @Column(name = "id")
    private long id;

    @Column(name = "option_specifier", nullable = false)
    private String optionSpecifier;

    @Column(name = "webapp", columnDefinition = "boolean default true", nullable = false)
    private boolean webapp = true;

    @Column(name = "email", columnDefinition = "boolean default false", nullable = false)
    private boolean email = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("notificationOption")
    private User user;

    @Override
    public Long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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
        return "NotificationOption{" + "id=" + id + ", optionSpecifier='" + optionSpecifier + '\'' + ", webapp=" + webapp + ", email=" + email + ", user=" + user + '}';
    }
}
