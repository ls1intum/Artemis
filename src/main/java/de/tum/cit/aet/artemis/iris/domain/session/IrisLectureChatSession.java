package de.tum.cit.aet.artemis.iris.domain.session;

import java.util.Optional;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

@Entity
@DiscriminatorValue("LECTURE_CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisLectureChatSession extends IrisChatSession {

    @ManyToOne
    @JsonIgnore
    private Lecture lecture;

    public IrisLectureChatSession() {
    }

    public IrisLectureChatSession(Lecture lecture, User user) {
        super(user);
        this.lecture = lecture;
    }

    public Lecture getLecture() {
        return lecture;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
    }

    @Override
    public String toString() {
        return "IrisLectureChatSession{" + "user=" + Optional.ofNullable(getUser()).map(User::getLogin).orElse("null") + ", lecture=" + lecture + '}';
    }
}
