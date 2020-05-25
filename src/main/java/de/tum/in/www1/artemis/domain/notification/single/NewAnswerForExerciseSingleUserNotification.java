package de.tum.in.www1.artemis.domain.notification.single;

import java.io.Serializable;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.tum.in.www1.artemis.domain.StudentQuestionAnswer;

@Entity
@DiscriminatorValue(value = "U-NAFE")
@JsonTypeName("single-newAnswerForExercise")
public class NewAnswerForExerciseSingleUserNotification extends SingleUserNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne(targetEntity = StudentQuestionAnswer.class)
    @JoinColumn(name = "notification_target")
    private StudentQuestionAnswer notificationTarget;

    public NewAnswerForExerciseSingleUserNotification() {
    }

    public NewAnswerForExerciseSingleUserNotification(StudentQuestionAnswer answer) {
        super("New answer", "Your Question got answered.", answer.getAuthor(), answer.getQuestion().getAuthor());
        this.setNotificationTarget(answer);
    }

    public StudentQuestionAnswer getNotificationTarget() {
        return notificationTarget;
    }

    public NewAnswerForExerciseSingleUserNotification notificationTarget(StudentQuestionAnswer notificationTarget) {
        this.notificationTarget = notificationTarget;
        return this;
    }

    public void setNotificationTarget(StudentQuestionAnswer notificationTarget) {
        this.notificationTarget = notificationTarget;
    }
}
