package de.tum.in.www1.artemis.domain.notification.single;

import java.io.Serializable;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.StudentQuestionAnswer;

@Entity
@DiscriminatorValue(value = "U-NAFL")
public class NewAnswerForLectureSingleUserNotification extends SingleUserNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    public NewAnswerForLectureSingleUserNotification() {
    }

    public NewAnswerForLectureSingleUserNotification(StudentQuestionAnswer answer) {
        super("New answer", "Your Question got answered.", answer.getAuthor(), answer.getQuestion().getAuthor());
        this.setTarget(this.createTarget(answer));
    }

    private String createTarget(StudentQuestionAnswer studentQuestionAnswer) {
        JsonObject target = new JsonObject();
        target.addProperty("message", "newAnswer");
        target.addProperty("id", studentQuestionAnswer.getQuestion().getLecture().getId());
        target.addProperty("entity", "lectures");
        target.addProperty("course", studentQuestionAnswer.getQuestion().getLecture().getCourse().getId());
        target.addProperty("mainPage", "courses");
        return target.toString();
    }
}
