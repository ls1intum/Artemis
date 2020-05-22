package de.tum.in.www1.artemis.domain.notification.group;

import java.io.Serializable;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;

@Entity
@DiscriminatorValue(value = "G-AU")
public class AttachmentUpdatedGroupNotification extends GroupNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    public AttachmentUpdatedGroupNotification() {
    }

    public AttachmentUpdatedGroupNotification(User author, GroupNotificationType groupNotificationType, String notificationText, Attachment attachment) {
        super("Attachment updated", "Attachment \"" + attachment.getName() + "\" updated.", author, attachment.getLecture().getCourse(), groupNotificationType);
        this.setTarget(super.getLectureTarget(attachment.getLecture(), "attachmentUpdated"));
        if (notificationText != null) {
            this.setText(notificationText);
        }
    }
}
