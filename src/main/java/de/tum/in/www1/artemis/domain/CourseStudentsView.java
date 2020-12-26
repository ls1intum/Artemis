package de.tum.in.www1.artemis.domain;

import java.io.Serializable;

import javax.persistence.*;

import org.hibernate.annotations.Immutable;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "view_students_of_course")
@Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseStudentsView {

    @EmbeddedId
    private CourseStudentViewId courseStudentViewId;

    public CourseStudentViewId getCourseStudentViewId() {
        return courseStudentViewId;
    }

    public void setCourseStudentViewId(CourseStudentViewId courseStudentViewId) {
        this.courseStudentViewId = courseStudentViewId;
    }

    public CourseStudentsView(CourseStudentViewId courseStudentViewId) {
        this.courseStudentViewId = courseStudentViewId;
    }

    /**
     * Empty constructor needed for jackson
     */
    public CourseStudentsView() {
    }

    @Immutable
    @Embeddable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class CourseStudentViewId implements Serializable {

        @Column(name = "COURSE_ID")
        private long courseId;

        @Column(name = "STUDENT_ID")
        private long studentId;

        /**
         * Empty constructor needed for jackson
         */
        public CourseStudentViewId() {

        }

        public CourseStudentViewId(long courseId, long studentId) {
            this.courseId = courseId;
            this.studentId = studentId;
        }

        public long getCourseId() {
            return courseId;
        }

        public void setCourseId(long courseId) {
            this.courseId = courseId;
        }

        public long getStudentId() {
            return studentId;
        }

        public void setStudentId(long studentId) {
            this.studentId = studentId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CourseStudentViewId that = (CourseStudentViewId) o;

            if (courseId != that.courseId) {
                return false;
            }
            return studentId == that.studentId;
        }

        @Override
        public int hashCode() {
            int result = (int) (courseId ^ (courseId >>> 32));
            result = 31 * result + (int) (studentId ^ (studentId >>> 32));
            return result;
        }
    }
}
