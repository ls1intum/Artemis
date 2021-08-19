package de.tum.in.www1.artemis.domain.notification;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.User;

/**
 * Individual User Options that make the User Settings
 */
@Entity
@Table(name = "user_option")
// @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserOption extends DomainObject {

    @Id
    @Column(name = "id")
    private long id;

    @Column(name = "category")
    private String category;

    @Column(name = "group")
    private String group;

    @Column(name = "description")
    private String description;

    @Column(name = "webapp", columnDefinition = "boolean default true")
    private boolean webapp = true;

    @Column(name = "email", columnDefinition = "boolean default false")
    private boolean email = false;

    @ManyToOne
    @JoinColumn(name = "user")
    private User user;

    // getter & setter

    @Override
    public Long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
        return "UserOption{" + "id=" + id + ", category='" + category + '\'' + ", group='" + group + '\'' + ", description='" + description + '\'' + ", webapp=" + webapp
                + ", email=" + email + ", user=" + user + '}';
    }
}
