package de.tum.cit.aet.artemis.core.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.account.domain.User;

@Entity
@Table(name = "user_course_role")
@IdClass(UserCourseRole.UserCourseRoleId.class)
public class UserCourseRole implements Serializable {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "course_role", nullable = false)
    private CourseRole role;

    public UserCourseRole() {
    }

    public UserCourseRole(User user, Course course, CourseRole role) {
        this.user = user;
        this.course = course;
        this.role = role;
    }

    public User getUser() {
        return user;
    }

    public Course getCourse() {
        return course;
    }

    public CourseRole getRole() {
        return role;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        UserCourseRole that = (UserCourseRole) other;
        return Objects.equals(user != null ? user.getId() : null, that.user != null ? that.user.getId() : null)
                && Objects.equals(course != null ? course.getId() : null, that.course != null ? that.course.getId() : null) && role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(user != null ? user.getId() : null, course != null ? course.getId() : null, role);
    }

    public static class UserCourseRoleId implements Serializable {

        private Long user;

        private Long course;

        private CourseRole role;

        public UserCourseRoleId() {
        }

        public UserCourseRoleId(Long user, Long course, CourseRole role) {
            this.user = user;
            this.course = course;
            this.role = role;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            UserCourseRoleId that = (UserCourseRoleId) other;
            return Objects.equals(user, that.user) && Objects.equals(course, that.course) && role == that.role;
        }

        @Override
        public int hashCode() {
            return Objects.hash(user, course, role);
        }
    }
}
