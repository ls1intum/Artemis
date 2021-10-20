package de.tum.in.www1.artemis.domain.notification;

import java.time.ZonedDateTime;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;

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
     * @param answerPost an answer for an exercise post
     * @return JSON string with all properties for the notification target field
     */
    public String answerPostTargetForExercise(AnswerPost answerPost) {
        JsonObject target = new JsonObject();
        target.addProperty("message", "newAnswer");
        target.addProperty("id", answerPost.getPost().getExercise().getId());
        target.addProperty("entity", "exercises");
        target.addProperty("course", answerPost.getPost().getExercise().getCourseViaExerciseGroupOrCourseMember().getId());
        target.addProperty("mainPage", "courses");
        return target.toString();
    }

    /**
     * Set the target JSON string for a lecture notification
     *
     * @param answerPost an answer for a lecture post
     * @return JSON string with all properties for the notification target field
     */
    public String answerPostTargetForLecture(AnswerPost answerPost) {
        JsonObject target = new JsonObject();
        target.addProperty("message", "newAnswer");
        target.addProperty("id", answerPost.getPost().getLecture().getId());
        target.addProperty("entity", "lectures");
        target.addProperty("course", answerPost.getPost().getLecture().getCourse().getId());
        target.addProperty("mainPage", "courses");
        return target.toString();
    }

    @Override
    public String toString() {
        return "SingleUserNotification{" + "id=" + getId() + "}";
    }
}
