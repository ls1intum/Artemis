package de.tum.in.www1.artemis.domain.notification.group;

import java.io.Serializable;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonTypeName;

import de.tum.in.www1.artemis.domain.StudentQuestionAnswer;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;

@Entity
@DiscriminatorValue(value = "G-NAFE")
@JsonTypeName("group-newAnswerForExercise")
public class NewAnswerForExerciseGroupNotification extends GroupNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne(targetEntity = StudentQuestionAnswer.class)
    @JoinColumn(name = "notification_target")
    private StudentQuestionAnswer notificationTarget;

    public NewAnswerForExerciseGroupNotification() {
    }

    public NewAnswerForExerciseGroupNotification(User author, GroupNotificationType groupNotificationType, StudentQuestionAnswer answer) {
        super("New Answer", "Exercise \"" + answer.getQuestion().getExercise().getTitle() + "\" got a new answer.", author, answer.getQuestion().getExercise().getCourse(),
                groupNotificationType);
        this.setNotificationTarget(answer);
    }

    public StudentQuestionAnswer getNotificationTarget() {
        return notificationTarget;
    }

    public NewAnswerForExerciseGroupNotification notificationTarget(StudentQuestionAnswer notificationTarget) {
        this.notificationTarget = notificationTarget;
        return this;
    }

    public void setNotificationTarget(StudentQuestionAnswer notificationTarget) {
        this.notificationTarget = notificationTarget;
    }
}
