package de.tum.in.www1.artemis.domain.notification.group;

import java.io.Serializable;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonTypeName;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;

@Entity
@DiscriminatorValue(value = "G-EU")
@JsonTypeName("group-exerciseUpdated")
public class ExerciseUpdatedGroupNotification extends GroupNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne(targetEntity = Exercise.class)
    @JoinColumn(name = "notification_target")
    private Exercise notificationTarget;

    public ExerciseUpdatedGroupNotification() {
    }

    public ExerciseUpdatedGroupNotification(User author, GroupNotificationType groupNotificationType, String notificationText, Exercise exercise) {
        super("Exercise \"" + exercise.getTitle() + "\" updated.", author, exercise.getCourse(), groupNotificationType);
        this.setNotificationTarget(exercise);
        if (notificationText != null) {
            this.setText(notificationText);
        }
    }

    public Exercise getNotificationTarget() {
        return notificationTarget;
    }

    public ExerciseUpdatedGroupNotification notificationTarget(Exercise notificationTarget) {
        this.notificationTarget = notificationTarget;
        return this;
    }

    public void setNotificationTarget(Exercise notificationTarget) {
        this.notificationTarget = notificationTarget;
    }
}
