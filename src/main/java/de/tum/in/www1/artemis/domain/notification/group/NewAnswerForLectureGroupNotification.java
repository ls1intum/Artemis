package de.tum.in.www1.artemis.domain.notification.group;

import java.io.Serializable;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonTypeName;

import de.tum.in.www1.artemis.domain.StudentQuestionAnswer;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;

@Entity
@DiscriminatorValue(value = "G-NAFL")
@JsonTypeName("group-newAnswerForLecture")
public class NewAnswerForLectureGroupNotification extends GroupNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    public NewAnswerForLectureGroupNotification() {
    }

    public NewAnswerForLectureGroupNotification(User author, GroupNotificationType groupNotificationType, StudentQuestionAnswer answer) {
        super("New Answer", "Lecture \"" + answer.getQuestion().getLecture().getTitle() + "\" got a new answer.", author, answer.getQuestion().getLecture().getCourse(),
                groupNotificationType);
        this.setTarget(super.getLectureAnswerTarget(answer.getQuestion().getLecture()));
    }
}
