package de.tum.in.www1.artemis.domain.notification;

import java.time.ZonedDateTime;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;

/**
 * A Notification.
 */
@Entity
@Table(name = "notification")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "N")
@DiscriminatorOptions(force = true)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "notificationType")
// Annotation necessary to distinguish between concrete implementations of Notification when deserializing from JSON
@JsonSubTypes({ @JsonSubTypes.Type(value = GroupNotification.class, name = "group"), @JsonSubTypes.Type(value = SingleUserNotification.class, name = "single"),
        @JsonSubTypes.Type(value = SystemNotification.class, name = "system") })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Notification extends DomainObject {

    @Column(name = "title")
    private String title;

    // @Lob
    @Column(name = "text")
    private String text;

    @Column(name = "notification_date")
    private ZonedDateTime notificationDate;

    @Column(name = "target")
    private String target;

    /**
     * The String target is created based on a custom JAVA class
     * which hold the needed information to build a valid URL/Link
     * it is used to create Emails without the need to parse the target (e.g. via GSON)
     */
    @Transient
    private transient NotificationTarget targetTransient;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", columnDefinition = "varchar(15) default 'MEDIUM'")
    private NotificationPriority priority = NotificationPriority.MEDIUM;

    @Column(name = "outdated", columnDefinition = "boolean default false")
    private boolean outdated = false;

    @ManyToOne
    private User author;

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

    public NotificationTarget getTargetTransient() {
        return targetTransient;
    }

    public void setTargetTransient(NotificationTarget targetTransient) {
        this.targetTransient = targetTransient;
    }

    public void setTransientAndStringTarget(NotificationTarget targetTransient) {
        this.setTargetTransient(targetTransient);
        this.setTarget(targetTransient.toJsonString());
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public Notification priority(NotificationPriority priority) {
        this.priority = priority;
        return this;
    }

    public void setPriority(NotificationPriority priority) {
        this.priority = priority;
    }

    public boolean isOutdated() {
        return outdated;
    }

    public Notification outdated(boolean outdated) {
        this.outdated = outdated;
        return this;
    }

    public void setOutdated(boolean outdated) {
        this.outdated = outdated;
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

    @Override
    public String toString() {
        return "Notification{" + "title='" + title + '\'' + ", text='" + text + '\'' + ", notificationDate=" + notificationDate + ", target='" + target + '\'' + ", priority="
                + priority + ", outdated=" + outdated + ", author=" + author + '}';
    }
}
