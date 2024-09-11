package de.tum.cit.aet.artemis.domain.iris.session;

import java.util.Optional;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;

/**
 * An IrisCourseChatSession represents a conversation between a user and an LLM.
 * This is used for students receiving tutor assistance from Iris while looking at the course dashboard.
 */
@Entity
@DiscriminatorValue("COURSE_CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisCourseChatSession extends IrisChatSession {

    @ManyToOne
    @JsonIgnore
    private Course course;

    public IrisCourseChatSession() {
    }

    public IrisCourseChatSession(Course course, User user) {
        super(user);
        this.course = course;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    @Override
    public String toString() {
        return "IrisCourseChatSession{" + "user=" + Optional.ofNullable(getUser()).map(User::getLogin).orElse("null") + "," + "course=" + course + '}';
    }
}
