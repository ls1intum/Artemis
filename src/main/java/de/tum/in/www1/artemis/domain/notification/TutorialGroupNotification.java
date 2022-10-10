package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_DELETED;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_UPDATED;

import java.time.ZonedDateTime;
import java.util.Set;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;

/**
 * A Notification concerning all students and the assigned tutor of a tutorial group.
 */
@Entity
@DiscriminatorValue("T")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorialGroupNotification extends Notification {

    @JsonIgnore
    public final static Set<NotificationType> TUTORIAL_GROUP_NOTIFICATION_TYPES = Set.of(TUTORIAL_GROUP_DELETED, TUTORIAL_GROUP_UPDATED);

    // ToDo: Idea: Reuse jhi_type_column to allow tutorial group notifications to be sent to only for example officially registered students and not self registered students
    @ManyToOne
    @JoinColumn(name = "tutorial_group_id")
    private TutorialGroup tutorialGroup;

    /**
     * Added here to make it more convenient to create tutorial group notifications without having to pass the type as an argument all the time
     */
    @Transient
    @JsonIgnore
    public NotificationType notificationType;

    public TutorialGroupNotification() {
        // Empty constructor needed for Jackson.
    }

    public TutorialGroupNotification(TutorialGroup tutorialGroup, String title, String text, NotificationType notificationType) {
        verifySupportedNotificationType(notificationType);
        this.notificationType = notificationType;
        this.setTutorialGroup(tutorialGroup);
        this.setNotificationDate(ZonedDateTime.now());
        this.setTitle(title);
        this.setText(text);
    }

    public TutorialGroup getTutorialGroup() {
        return tutorialGroup;
    }

    public void setTutorialGroup(TutorialGroup tutorialGroup) {
        this.tutorialGroup = tutorialGroup;
    }

    /**
     * Websocket notification channel for tutorial group notifications of a specific tutorial group
     *
     * @return the channel
     */
    public String getTopic() {
        return "/topic/tutorial-group/" + tutorialGroup.getId() + "/notifications";
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    /**
     * Verifies that the given notification type is supported as a TutorialGroupNotification
     *
     * @param notificationType the notification type to verify
     */
    public static void verifySupportedNotificationType(NotificationType notificationType) {
        if (!TUTORIAL_GROUP_NOTIFICATION_TYPES.contains(notificationType)) {
            throw new UnsupportedOperationException("Unsupported NotificationType for tutorial groups: " + notificationType);
        }
    }

}
