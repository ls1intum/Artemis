package de.tum.in.www1.artemis.domain.notification.group;

import java.io.Serializable;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonTypeName;

import de.tum.in.www1.artemis.domain.StudentQuestionAnswer;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;

@Entity
@DiscriminatorValue(value = "G-NAFE")
@JsonTypeName("group-newAnswerForExercise")
public class NewAnswerForExerciseGroupNotification extends GroupNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    public NewAnswerForExerciseGroupNotification() {
    }

    public NewAnswerForExerciseGroupNotification(User author, GroupNotificationType groupNotificationType, StudentQuestionAnswer answer) {
        super("New Answer", "Exercise \"" + answer.getQuestion().getExercise().getTitle() + "\" got a new answer.", author, answer.getQuestion().getExercise().getCourse(),
                groupNotificationType);
        this.setTarget(super.getExerciseAnswerTarget(answer.getQuestion().getExercise()));
    }
}
