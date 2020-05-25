package de.tum.in.www1.artemis.domain.notification.group;

import java.io.Serializable;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonTypeName;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;

@Entity
@DiscriminatorValue(value = "G-AU")
@JsonTypeName("group-attachmentUpdated")
public class AttachmentUpdatedGroupNotification extends GroupNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne(targetEntity = Attachment.class)
    @JoinColumn(name = "notification_target")
    private Attachment notificationTarget;

    public AttachmentUpdatedGroupNotification() {
    }

    public AttachmentUpdatedGroupNotification(User author, GroupNotificationType groupNotificationType, String notificationText, Attachment attachment) {
        super("Attachment updated", "Attachment \"" + attachment.getName() + "\" updated.", author, attachment.getLecture().getCourse(), groupNotificationType);
        this.setNotificationTarget(attachment);
        if (notificationText != null) {
            this.setText(notificationText);
        }
    }

    public Attachment getNotificationTarget() {
        return notificationTarget;
    }

    public AttachmentUpdatedGroupNotification notificationTarget(Attachment notificationTarget) {
        this.notificationTarget = notificationTarget;
        return this;
    }

    public void setNotificationTarget(Attachment notificationTarget) {
        this.notificationTarget = notificationTarget;
    }
}
