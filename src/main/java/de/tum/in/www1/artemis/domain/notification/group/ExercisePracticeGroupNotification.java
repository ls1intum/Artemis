package de.tum.in.www1.artemis.domain.notification.group;

import java.io.Serializable;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;

@Entity
@DiscriminatorValue(value = "G-EP")
public class ExercisePracticeGroupNotification extends GroupNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    public ExercisePracticeGroupNotification() {
    }

    public ExercisePracticeGroupNotification(User author, GroupNotificationType groupNotificationType, Exercise exercise) {
        super("Exercise open for practice", "Exercise \"" + exercise.getTitle() + "\" is now open for practice.", author, exercise.getCourse(), groupNotificationType);
        this.setTarget(super.getExerciseUpdatedTarget(exercise));
    }
}
