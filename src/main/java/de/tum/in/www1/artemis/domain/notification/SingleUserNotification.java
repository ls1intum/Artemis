package de.tum.in.www1.artemis.domain.notification;

import java.time.ZonedDateTime;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;

/**
 * A SingleUserNotification.
 */
@Entity
@DiscriminatorValue(value = "U")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SingleUserNotification extends Notification {

    /**
     * Specifies how this notification was created : NEW_ANSWER_POST_FOR_EXERCISE, NEW_ANSWER_POST_FOR_LECTURE, ...
     * Otherwise this information is lost for the client-side
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "original_notification_type", columnDefinition = "enum default UNSPECIFIED")
    private NotificationType originalNotificationType = NotificationType.UNSPECIFIED;

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

    public void setOriginalNotificationType(NotificationType originalNotificationType) {
        this.originalNotificationType = originalNotificationType;
    }

    public NotificationType getOriginalNotificationType() {
        return this.originalNotificationType;
    }

    public SingleUserNotification originalNotificationType(NotificationType originalNotificationType) {
        this.originalNotificationType = originalNotificationType;
        return this;
    }

    public SingleUserNotification() {
    }

    public SingleUserNotification(User recipient, User author, String title, String text, NotificationType originalNotificationType) {
        this.setRecipient(recipient);
        this.setAuthor(author);
        this.setNotificationDate(ZonedDateTime.now());
        this.setTitle(title);
        this.setText(text);
        this.setOriginalNotificationType(originalNotificationType);
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
        return "SingleUserNotification{" + "id=" + getId() + ", originalNotificationType='" + getOriginalNotificationType() + "}";
    }
}
