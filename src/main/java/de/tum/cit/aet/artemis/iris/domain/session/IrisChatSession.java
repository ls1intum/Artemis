package de.tum.cit.aet.artemis.iris.domain.session;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

@Entity
@DiscriminatorValue("CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisChatSession extends IrisSession {

    // TODO: JsonIgnore entfernen ?
    private long userId;

    @JsonIgnore
    private long courseId;

    // Nullable
    @JsonIgnore
    private Long exerciseId;

    // Nullable
    @JsonIgnore
    private Long lectureId;

    public IrisChatSession() {
    }

    public IrisChatSession(Course course, User user) {
        this.userId = user.getId();
        this.courseId = course.getId();
    }

    public IrisChatSession(Exercise exercise, User user) {
        this.userId = user.getId();
        this.exerciseId = exercise.getId();
        this.courseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();
    }

    public IrisChatSession(Lecture lecture, User user) {
        this.userId = user.getId();
        this.lectureId = lecture.getId();
        this.courseId = lecture.getCourse().getId();
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getCourseId() {
        return courseId;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Long getLectureId() {
        return lectureId;
    }

    public void setLectureId(Long lectureId) {
        this.lectureId = lectureId;
    }

    @Override
    public boolean shouldSelectLLMUsage() {
        return true;
    }

    public IrisChatMode getMode() {
        return IrisChatMode.CHAT;
    }
}
