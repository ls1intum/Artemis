package de.tum.cit.aet.artemis.iris.domain.session;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
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

    @JsonIgnore
    @Column(name = "in_ask_user_mode_pipeline", nullable = false)
    private boolean inAskUserModePipeline = false;

    @JsonIgnore
    @Column(name = "in_class_quiz", nullable = false)
    private boolean inClassQuiz = false;

    @JsonIgnore
    @Column(name = "questions_asked", nullable = false)
    private int questionsAsked = 0;

    public IrisChatSession() {
    }

    public IrisChatSession(Course course, User user) {
        setUserId(user.getId());
        this.courseId = course.getId();
        this.entityId = course.getId();
        this.chatMode = IrisChatMode.COURSE_CHAT;
    }

    public IrisChatSession(Exercise exercise, User user, IrisChatMode chatMode) {
        if (chatMode != IrisChatMode.PROGRAMMING_EXERCISE_CHAT && chatMode != IrisChatMode.TEXT_EXERCISE_CHAT) {
            throw new IllegalArgumentException("Exercise-based IrisChatSession requires an exercise chat mode (PROGRAMMING_EXERCISE_CHAT or TEXT_EXERCISE_CHAT), got: " + chatMode);
        }
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

    public IrisChatMode getMode() {
        return chatMode;
    }

    public void setMode(IrisChatMode chatMode) {
        this.chatMode = chatMode;
    }

    public boolean isInAskUserModePipeline() {
        return inAskUserModePipeline;
    }

    public void setInAskUserModePipeline(boolean inAskUserModePipeline) {
        this.inAskUserModePipeline = inAskUserModePipeline;
    }

    public boolean isInClassQuiz() {
        return inClassQuiz;
    }

    public void setInClassQuiz(boolean inClassQuiz) {
        this.inClassQuiz = inClassQuiz;
    }

    public int getQuestionsAsked() {
        return questionsAsked;
    }

    public void setQuestionsAsked(int questionsAsked) {
        this.questionsAsked = questionsAsked;
    }
}
