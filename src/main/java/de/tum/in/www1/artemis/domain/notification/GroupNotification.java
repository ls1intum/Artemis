package de.tum.in.www1.artemis.domain.notification;

import java.time.ZonedDateTime;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;

/**
 * A GroupNotification.
 */
@Entity
@DiscriminatorValue(value = "G")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GroupNotification extends Notification {

    @Enumerated(EnumType.STRING)
    @Column(name = "jhi_type")
    private GroupNotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "original_notification_type", columnDefinition = "enum default UNSPECIFIED")
    private NotificationType originalNotificationType = NotificationType.UNSPECIFIED;

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

    public void setOriginalNotificationType(NotificationType originalNotificationType) {
        this.originalNotificationType = originalNotificationType;
    }

    public NotificationType getOriginalNotificationType() {
        return this.originalNotificationType;
    }

    public GroupNotification originalNotificationType(NotificationType originalNotificationType) {
        this.originalNotificationType = originalNotificationType;
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
    /*
     * public GroupNotification(Course course, String title, String notificationText, User user, GroupNotificationType type) { this.setCourse(course); this.setType(type);
     * this.setNotificationDate(ZonedDateTime.now()); this.setTitle(title); this.setText(notificationText); this.setAuthor(user); }
     */

    public GroupNotification(Course course, String title, String notificationText, User user, GroupNotificationType type, NotificationType originalNotificationType) {
        this.setCourse(course);
        this.setType(type);
        this.setOriginalNotificationType(originalNotificationType);
        this.setNotificationDate(ZonedDateTime.now());
        this.setTitle(title);
        this.setText(notificationText);
        this.setAuthor(user);
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public String getExerciseCreatedTarget(Exercise exercise) {
        return getExerciseTarget(exercise, "exerciseCreated");
    }

    public String getExerciseUpdatedTarget(Exercise exercise) {
        return getExerciseTarget(exercise, "exerciseUpdated");
    }

    public String getExerciseAnswerPostTarget(Exercise exercise) {
        return getExerciseTarget(exercise, "newAnswerPost");
    }

    public String getExercisePostTarget(Exercise exercise) {
        return getExerciseTarget(exercise, "newPost");
    }

    public String getLecturePostTarget(Lecture lecture) {
        return getLectureTarget(lecture, "newPost");
    }

    public String getLectureAnswerPostTarget(Lecture lecture) {
        return getLectureTarget(lecture, "newAnswerPost");
    }

    public String getAttachmentUpdated(Lecture lecture) {
        return getLectureTarget(lecture, "attachmentUpdated");
    }

    /**
     * Create JSON representation for a GroupNotification for an ProgrammingExercise in an Exam or if duplicated test cases were detected.
     *
     * @param programmingExercise for which to create the notification.
     * @param message             to use for the notification.
     * @return the stringified JSON of the target.
     */
    public String getExamProgrammingExerciseOrTestCaseTarget(ProgrammingExercise programmingExercise, String message) {
        JsonObject target = new JsonObject();
        target.addProperty("message", message);
        target.addProperty("id", programmingExercise.getId());
        target.addProperty("entity", "programming-exercises");
        target.addProperty("course", programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        target.addProperty("mainPage", "course-management");
        return target.toString();
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
        target.addProperty("course", exercise.getCourseViaExerciseGroupOrCourseMember().getId());
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

    /**
     * Create JSON representation for a GroupNotification for a Course.
     *
     * @param course for which to create the notification.
     * @param message to use for the notification.
     * @return the stringified JSON of the target.
     */
    public String getCourseTarget(Course course, String message) {
        JsonObject target = new JsonObject();
        target.addProperty("message", message);
        target.addProperty("id", course.getId());
        target.addProperty("entity", "courses");
        target.addProperty("course", course.getId());
        target.addProperty("mainPage", "courses");
        return target.toString();
    }

    public String getTopic() {
        return "/topic/course/" + getCourse().getId() + "/" + getType();
    }

    // maybe
    // public String getTopic() {
    // return "/topic/course/" + getCourse().getId() + "/" + getType() + "/" + getOriginalNotificationType();
    // }

    /*
     * @Override public String toString() { return "GroupNotification{" + "id=" + getId() + ", type='" + getType() + "'" + "}"; }
     */

    @Override
    public String toString() {
        return "GroupNotification{" + "id=" + getId() + ", type='" + getType() + ", originalNotificationType='" + getOriginalNotificationType() + "'" + "}";
    }
}
