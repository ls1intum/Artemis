package de.tum.cit.aet.artemis.communication.domain.notification;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.domain.NotificationPriority;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * A Notification.
 */
@Entity
@Table(name = "notification")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "N")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "notificationType")
// Annotation necessary to distinguish between concrete implementations of Notification when deserializing from JSON
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = GroupNotification.class, name = "group"),
    @JsonSubTypes.Type(value = SingleUserNotification.class, name = "single"),
    @JsonSubTypes.Type(value = SystemNotification.class, name = "system"),
    @JsonSubTypes.Type(value = ConversationNotification.class, name = "conversation")
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class Notification extends DomainObject {

    private static final Logger log = LoggerFactory.getLogger(Notification.class);

    @Column(name = "title")
    private String title;

    @Column(name = "text")
    private String text;

    @Column(name = "text_is_placeholder")
    private boolean textIsPlaceholder;

    @Column(name = "placeholder_values")
    private String placeholderValues;

    // Only set when initially created and used by the instant notification system
    @JsonIgnore
    @Transient
    private String[] transientPlaceholderValues;

    @Column(name = "notification_date")
    private ZonedDateTime notificationDate;

    @Column(name = "target")
    private String target;

    /**
     * The String target is created based on a custom JAVA class
     * which hold the needed information to build a valid URL/Link
     * it is used to create Emails without the need to parse the target (e.g. via json)
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

    public boolean getTextIsPlaceholder() {
        return textIsPlaceholder;
    }

    public void setTextIsPlaceholder(boolean textIsPlaceholder) {
        this.textIsPlaceholder = textIsPlaceholder;
    }

    public String getPlaceholderValues() {
        return placeholderValues;
    }

    @JsonIgnore
    public String[] getTransientPlaceholderValuesAsArray() {
        return transientPlaceholderValues;
    }

    public void setPlaceholderValues(String notificationTextValues) {
        this.placeholderValues = notificationTextValues;
    }

    /**
     * @param notificationTextValues the notification text values as a string array
     *                                   We convert it to a json string, so we can store it in the database
     */
    public void setPlaceholderValues(String[] notificationTextValues) {
        transientPlaceholderValues = notificationTextValues;

        if (notificationTextValues == null || notificationTextValues.length == 0) {
            this.placeholderValues = null;
        }
        else {
            String jsonString = null;
            try {
                jsonString = new ObjectMapper().writeValueAsString(notificationTextValues);
            }
            catch (JsonProcessingException exception) {
                log.error(exception.getMessage(), exception);
            }
            this.placeholderValues = jsonString;
        }
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
