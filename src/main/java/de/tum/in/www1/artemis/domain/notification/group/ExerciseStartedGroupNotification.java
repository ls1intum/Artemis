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
@DiscriminatorValue(value = "G-ES")
@JsonTypeName("group-exerciseStarted")
public class ExerciseStartedGroupNotification extends GroupNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne(targetEntity = Exercise.class)
    @JoinColumn(name = "notification_target")
    private Exercise notificationTarget;

    public ExerciseStartedGroupNotification() {
    }

    public ExerciseStartedGroupNotification(User author, GroupNotificationType groupNotificationType, Exercise exercise) {
        super("Exercise started", "Exercise \"" + exercise.getTitle() + "\" just started.", author, exercise.getCourse(), groupNotificationType);
        this.setNotificationTarget(exercise);
    }

    public Exercise getNotificationTarget() {
        return notificationTarget;
    }

    public ExerciseStartedGroupNotification notificationTarget(Exercise notificationTarget) {
        this.notificationTarget = notificationTarget;
        return this;
    }

    public void setNotificationTarget(Exercise notificationTarget) {
        this.notificationTarget = notificationTarget;
    }
}
