package de.tum.in.www1.artemis.domain.notification;

import java.time.ZonedDateTime;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.StudentQuestionAnswer;
import de.tum.in.www1.artemis.domain.User;

/**
 * A SingleUserNotification.
 */
@Entity
@DiscriminatorValue(value = "U")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SingleUserNotification extends Notification {

    @ManyToOne
    private User recipient;

    public User getRecipient() {
        return recipient;
    }

    public void setRecipient(User user) {
        this.recipient = user;
    }

    public String getTopic() {
        return "/topic/user/" + getRecipient().getId() + "/notifications";
    }

    public SingleUserNotification() {
    }

    public SingleUserNotification(User recipient, User author, String title, String text) {
        this.setRecipient(recipient);
        this.setAuthor(author);
        this.setNotificationDate(ZonedDateTime.now());
        this.setTitle(title);
        this.setText(text);
    }

    /**
     * Set the target JSON string for an exercise notification
     *
     * @param studentQuestionAnswer a question that was posted for an exercise
     * @return JSON string with all properties for the notification target field
     */
    public String studentQuestionAnswerTargetForExercise(StudentQuestionAnswer studentQuestionAnswer) {
        JsonObject target = new JsonObject();
        target.addProperty("message", "newAnswer");
        target.addProperty("id", studentQuestionAnswer.getQuestion().getExercise().getId());
        target.addProperty("entity", "exercises");
        target.addProperty("course", studentQuestionAnswer.getQuestion().getExercise().getCourseViaExerciseGroupOrCourseMember().getId());
        target.addProperty("mainPage", "courses");
        return target.toString();
    }

    /**
     * Set the target JSON string for a lecture notification
     *
     * @param studentQuestionAnswer a question that was posted for a lecture
     * @return JSON string with all properties for the notification target field
     */
    public String studentQuestionAnswerTargetForLecture(StudentQuestionAnswer studentQuestionAnswer) {
        JsonObject target = new JsonObject();
        target.addProperty("message", "newAnswer");
        target.addProperty("id", studentQuestionAnswer.getQuestion().getLecture().getId());
        target.addProperty("entity", "lectures");
        target.addProperty("course", studentQuestionAnswer.getQuestion().getLecture().getCourse().getId());
        target.addProperty("mainPage", "courses");
        return target.toString();
    }

    @Override
    public String toString() {
        return "SingleUserNotification{" + "id=" + getId() + "}";
    }
}
