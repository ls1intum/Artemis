package de.tum.in.www1.artemis.domain.notification.single;

import java.io.Serializable;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonTypeName;
import de.tum.in.www1.artemis.domain.StudentQuestionAnswer;

@Entity
@DiscriminatorValue(value = "U-NAFL")
@JsonTypeName("single-newAnswerForLecture")
public class NewAnswerForLectureSingleUserNotification extends SingleUserNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne(targetEntity = StudentQuestionAnswer.class)
    @JoinColumn(name = "notification_target")
    private StudentQuestionAnswer notificationTarget;

    public NewAnswerForLectureSingleUserNotification() {
    }

    public NewAnswerForLectureSingleUserNotification(StudentQuestionAnswer answer) {
        super(null, answer.getAuthor(), answer.getQuestion().getAuthor(), answer.getQuestion().getLecture().getCourse());
        this.setNotificationTarget(answer);
    }

    public StudentQuestionAnswer getNotificationTarget() {
        return notificationTarget;
    }

    public NewAnswerForLectureSingleUserNotification notificationTarget(StudentQuestionAnswer notificationTarget) {
        this.notificationTarget = notificationTarget;
        return this;
    }

    public void setNotificationTarget(StudentQuestionAnswer notificationTarget) {
        this.notificationTarget = notificationTarget;
    }
}
