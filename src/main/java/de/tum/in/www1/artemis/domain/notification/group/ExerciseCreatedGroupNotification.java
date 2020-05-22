package de.tum.in.www1.artemis.domain.notification.group;

import java.io.Serializable;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;

@Entity
@DiscriminatorValue(value = "G-EC")
public class ExerciseCreatedGroupNotification extends GroupNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    public ExerciseCreatedGroupNotification() {
    }

    public ExerciseCreatedGroupNotification(User author, GroupNotificationType groupNotificationType, Exercise exercise) {
        super("Exercise created", "A new exercise \"" + exercise.getTitle() + "\" got created.", author, exercise.getCourse(), groupNotificationType);
        this.setTarget(super.getExerciseCreatedTarget(exercise));
    }
}
