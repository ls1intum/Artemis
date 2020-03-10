package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;

/**
 * A GroupNotification.
 */
@Entity
@DiscriminatorValue(value = "G")
public class GroupNotification extends Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    @Enumerated(EnumType.STRING)
    @Column(name = "jhi_type")
    private GroupNotificationType type;

    @ManyToOne
    @JsonIgnoreProperties("groupNotifications")
    private Course course;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

    public GroupNotificationType getType() {
        return type;
    }

    public GroupNotification type(GroupNotificationType type) {
        this.type = type;
        return this;
    }

    public void setType(GroupNotificationType type) {
        this.type = type;
    }

    public Course getCourse() {
        return course;
    }

    public GroupNotification course(Course course) {
        this.course = course;
        return this;
    }

    public GroupNotification() {
    }

    public GroupNotification(Course course, String title, String notificationText, User user, GroupNotificationType type) {
        this.setCourse(course);
        this.setType(type);
        this.setNotificationDate(ZonedDateTime.now());
        this.setTitle(title);
        this.setText(notificationText);
        this.setAuthor(user);
    }

    public void setCourse(Course course) {
        this.course = course;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GroupNotification groupNotification = (GroupNotification) o;
        if (groupNotification.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), groupNotification.getId());
    }

    public String getExerciseCreatedTarget(Exercise exercise) {
        return getExerciseTarget(exercise, "exerciseCreated");
    }

    public String getExerciseUpdatedTarget(Exercise exercise) {
        return getExerciseTarget(exercise, "exerciseUpdated");
    }

    public String getExerciseAnswerTarget(Exercise exercise) {
        return getExerciseTarget(exercise, "newAnswer");
    }

    public String getExerciseQuestionTarget(Exercise exercise) {
        return getExerciseTarget(exercise, "newQuestion");
    }

    public String getLectureQuestionTarget(Lecture lecture) {
        return getLectureTarget(lecture, "newQuestion");
    }

    public String getLectureAnswerTarget(Lecture lecture) {
        return getLectureTarget(lecture, "newAnswer");
    }

    public String getAttachmentUpdated(Lecture lecture) {
        return getLectureTarget(lecture, "attachmentUpdated");
    }

    /**
     * Create JSON representation for a GroupNotification for an Exercise.
     *
     * @param exercise for which to create the notification.
     * @param message to use for the notification.
     * @return the stringified JSON of the target.
     */
    public String getExerciseTarget(Exercise exercise, String message) {
        JsonObject target = new JsonObject();
        target.addProperty("message", message);
        target.addProperty("id", exercise.getId());
        target.addProperty("entity", "exercises");
        target.addProperty("course", exercise.getCourse().getId());
        target.addProperty("mainPage", "courses");
        return target.toString();
    }

    /**
     * Create JSON representation for a GroupNotification for a Lecture.
     *
     * @param lecture for which to create the notification.
     * @param message to use for the notification.
     * @return the stringified JSON of the target.
     */
    public String getLectureTarget(Lecture lecture, String message) {
        JsonObject target = new JsonObject();
        target.addProperty("message", message);
        target.addProperty("id", lecture.getId());
        target.addProperty("entity", "lectures");
        target.addProperty("course", lecture.getCourse().getId());
        target.addProperty("mainPage", "courses");
        return target.toString();
    }

    public String getTopic() {
        return "/topic/course/" + getCourse().getId() + "/" + getType();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "GroupNotification{" + "id=" + getId() + ", type='" + getType() + "'" + "}";
    }
}
