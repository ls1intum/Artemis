package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

import javax.persistence.*;

import com.google.gson.JsonObject;

/**
 * A SingleUserNotification.
 */
@Entity
@DiscriminatorValue(value = "U")
public class SingleUserNotification extends Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    private User recipient;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public User getRecipient() {
        return recipient;
    }

    public SingleUserNotification recipient(User user) {
        this.recipient = user;
        return this;
    }

    public void setRecipient(User user) {
        this.recipient = user;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

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
        target.addProperty("course", studentQuestionAnswer.getQuestion().getExercise().getCourse().getId());
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SingleUserNotification singleUserNotification = (SingleUserNotification) o;
        if (singleUserNotification.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), singleUserNotification.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "SingleUserNotification{" + "id=" + getId() + "}";
    }
}
