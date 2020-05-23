package de.tum.in.www1.artemis.domain.notification.group;

import java.io.Serializable;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonTypeName;

import de.tum.in.www1.artemis.domain.StudentQuestion;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;

@Entity
@DiscriminatorValue(value = "G-NQFE")
@JsonTypeName("group-newQuestionForExercise")
public class NewQuestionForExerciseGroupNotification extends GroupNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    public NewQuestionForExerciseGroupNotification() {
    }

    public NewQuestionForExerciseGroupNotification(User author, GroupNotificationType groupNotificationType, StudentQuestion question) {
        super("New Question", "Exercise \"" + question.getExercise().getTitle() + "\" got a new question.", author, question.getExercise().getCourse(), groupNotificationType);
        this.setTarget(super.getExerciseQuestionTarget(question.getExercise()));
    }
}
