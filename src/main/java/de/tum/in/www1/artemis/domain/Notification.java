package de.tum.in.www1.artemis.domain;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A Notification.
 */
@Entity
@Table(name = "notification")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(
    name = "discriminator",
    discriminatorType = DiscriminatorType.STRING
)
@DiscriminatorValue(value = "N")
// NOTE: Use strict cache to prevent lost updates
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
// Annotation necessary to distinguish between concrete implementations of Notification when deserializing from JSON
@JsonSubTypes({
    @JsonSubTypes.Type(value = GroupNotification.class, name = "group"),
    @JsonSubTypes.Type(value = SingleUserNotification.class, name = "single"),
    @JsonSubTypes.Type(value = SystemNotification.class, name = "system"),
})
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Notification implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "text")
    private String text;

    @Column(name = "notification_date")
    private ZonedDateTime notificationDate;

    @Column(name = "target")
    private String target;

    @ManyToOne
    private User author;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public Notification title(String title) {
        this.title = title;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public Notification text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public ZonedDateTime getNotificationDate() {
        return notificationDate;
    }

    public Notification notificationDate(ZonedDateTime notificationDate) {
        this.notificationDate = notificationDate;
        return this;
    }

    public void setNotificationDate(ZonedDateTime notificationDate) {
        this.notificationDate = notificationDate;
    }

    public String getTarget() {
        return target;
    }

    public Notification target(String target) {
        this.target = target;
        return this;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public User getAuthor() {
        return author;
    }

    public Notification author(User user) {
        this.author = user;
        return this;
    }

    public void setAuthor(User user) {
        this.author = user;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Notification notification = (Notification) o;
        if (notification.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), notification.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Notification{" +
            "id=" + getId() +
            ", title='" + getTitle() + "'" +
            ", text='" + getText() + "'" +
            ", notificationDate='" + getNotificationDate() + "'" +
            ", target='" + getTarget() + "'" +
            "}";
    }
}
