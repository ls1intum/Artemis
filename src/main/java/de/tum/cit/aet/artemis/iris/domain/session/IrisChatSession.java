package de.tum.cit.aet.artemis.iris.domain.session;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

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

    @JsonIgnore
    private long courseId;

    @JsonIgnore
    private long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_mode")
    private IrisChatMode chatMode;

    public IrisChatSession() {
    }

    public IrisChatSession(Course course, User user) {
        setUserId(user.getId());
        this.courseId = course.getId();
        this.entityId = course.getId();
        this.chatMode = IrisChatMode.COURSE_CHAT;
    }

    public IrisChatSession(Exercise exercise, User user, IrisChatMode chatMode) {
        setUserId(user.getId());
        this.entityId = exercise.getId();
        this.courseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();
        this.chatMode = chatMode;
    }

    public IrisChatSession(Lecture lecture, User user) {
        setUserId(user.getId());
        this.entityId = lecture.getId();
        this.courseId = lecture.getCourse().getId();
        this.chatMode = IrisChatMode.LECTURE_CHAT;
    }

    public long getCourseId() {
        return courseId;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    @Override
    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(long entityId) {
        this.entityId = entityId;
    }

    @Override
    public boolean shouldSelectLLMUsage() {
        return true;
    }

    @Override
    public IrisChatMode getMode() {
        return chatMode;
    }
}
