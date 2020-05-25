package de.tum.in.www1.artemis.domain.notification.group;

import java.io.Serializable;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonTypeName;

import de.tum.in.www1.artemis.domain.StudentQuestion;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;

@Entity
@DiscriminatorValue(value = "G-NQFL")
@JsonTypeName("group-newQuestionForLecture")
public class NewQuestionForLectureGroupNotification extends GroupNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne(targetEntity = StudentQuestion.class)
    @JoinColumn(name = "notification_target")
    private StudentQuestion notificationTarget;

    public NewQuestionForLectureGroupNotification() {
    }

    public NewQuestionForLectureGroupNotification(User author, GroupNotificationType groupNotificationType, StudentQuestion question) {
        super("New Question", "Lecture \"" + question.getLecture().getTitle() + "\" got a new question.", author, question.getLecture().getCourse(), groupNotificationType);
        this.setNotificationTarget(question);
    }

    public StudentQuestion getNotificationTarget() {
        return notificationTarget;
    }

    public NewQuestionForLectureGroupNotification notificationTarget(StudentQuestion notificationTarget) {
        this.notificationTarget = notificationTarget;
        return this;
    }

    public void setNotificationTarget(StudentQuestion notificationTarget) {
        this.notificationTarget = notificationTarget;
    }
}
