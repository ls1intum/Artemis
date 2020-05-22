package de.tum.in.www1.artemis.domain.notification.group;

import java.io.Serializable;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import de.tum.in.www1.artemis.domain.StudentQuestion;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;

@Entity
@DiscriminatorValue(value = "G-NQFL")
public class NewQuestionForLectureGroupNotification extends GroupNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    public NewQuestionForLectureGroupNotification() {
    }

    public NewQuestionForLectureGroupNotification(User author, GroupNotificationType groupNotificationType, StudentQuestion question) {
        super("New Question", "Lecture \"" + question.getLecture().getTitle() + "\" got a new question.", author, question.getLecture().getCourse(), groupNotificationType);
        this.setTarget(super.getLectureQuestionTarget(question.getLecture()));
    }
}
