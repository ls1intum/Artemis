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
@DiscriminatorValue(value = "G-EP")
@JsonTypeName("group-exercisePractice")
public class ExercisePracticeGroupNotification extends GroupNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne(targetEntity = Exercise.class)
    @JoinColumn(name = "notification_target")
    private Exercise notificationTarget;

    public ExercisePracticeGroupNotification() {
    }

    public ExercisePracticeGroupNotification(User author, GroupNotificationType groupNotificationType, Exercise exercise) {
        super("Exercise open for practice", "Exercise \"" + exercise.getTitle() + "\" is now open for practice.", author, exercise.getCourse(), groupNotificationType);
        this.setNotificationTarget(exercise);
    }

    public Exercise getNotificationTarget() {
        return notificationTarget;
    }

    public ExercisePracticeGroupNotification notificationTarget(Exercise notificationTarget) {
        this.notificationTarget = notificationTarget;
        return this;
    }

    public void setNotificationTarget(Exercise notificationTarget) {
        this.notificationTarget = notificationTarget;
    }
}
