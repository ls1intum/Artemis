package de.tum.cit.aet.artemis.iris.domain.session;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * An IrisCourseChatSession represents a conversation between a user and an LLM.
 * This is used for students receiving tutor assistance from Iris while looking at the course dashboard.
 */
@Entity
@DiscriminatorValue("COURSE_CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisCourseChatSession extends IrisChatSession {

    @JsonIgnore
    private long courseId;

    public IrisCourseChatSession() {
    }

    public IrisCourseChatSession(Course course, User user) {
        super(user);
        this.courseId = course.getId();
    }

    public long getCourseId() {
        return courseId;
    }

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    @Override
    public boolean shouldSelectLLMUsage() {
        return true;
    }

    @Override
    public IrisChatMode getMode() {
        return IrisChatMode.COURSE_CHAT;
    }
}
