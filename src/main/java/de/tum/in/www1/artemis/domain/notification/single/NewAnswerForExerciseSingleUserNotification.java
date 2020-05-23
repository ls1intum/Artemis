package de.tum.in.www1.artemis.domain.notification.single;

import java.io.Serializable;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.StudentQuestionAnswer;

@Entity
@DiscriminatorValue(value = "U-NAFE")
@JsonTypeName("single-newAnswerForExercise")
public class NewAnswerForExerciseSingleUserNotification extends SingleUserNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    public NewAnswerForExerciseSingleUserNotification() {
    }

    public NewAnswerForExerciseSingleUserNotification(StudentQuestionAnswer answer) {
        super("New answer", "Your Question got answered.", answer.getAuthor(), answer.getQuestion().getAuthor());
        this.setTarget(this.createTarget(answer));
    }

    private String createTarget(StudentQuestionAnswer studentQuestionAnswer) {
        JsonObject target = new JsonObject();
        target.addProperty("message", "newAnswer");
        target.addProperty("id", studentQuestionAnswer.getQuestion().getExercise().getId());
        target.addProperty("entity", "exercises");
        target.addProperty("course", studentQuestionAnswer.getQuestion().getExercise().getCourse().getId());
        target.addProperty("mainPage", "courses");
        return target.toString();
    }
}
