package de.tum.in.www1.artemis.service.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;

/**
 * DTO for sending lectures and user who wants to access those lectures
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserLectureDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Set<Lecture> lectures;

    private User user;

    public UserLectureDTO() {
        // Needed from the object mapper in order to construct the object
    }

    public UserLectureDTO(Set<Lecture> exercises, User user) {
        this.lectures = exercises;
        this.user = user;
    }

    public Set<Lecture> getLectures() {
        return lectures;
    }

    public void setLectures(Set<Lecture> lectures) {
        this.lectures = lectures;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public String toString() {
        return String.format("UserLectureDTO{lectures=%s, user=%s}", getLectures(), getUser());
    }
}
