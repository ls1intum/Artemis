package de.tum.cit.aet.artemis.iris.domain.session;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * TODO: DELETE this class — replaced by {@link IrisChatSession} with courseId field. See Ticket 4.
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
        super();
        setUserId(user.getId());
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
        return IrisChatMode.CHAT;
    }
}
