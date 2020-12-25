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

    public CourseStudentsView(CourseStudentViewId courseStudentViewId) {
        this.courseStudentViewId = courseStudentViewId;
    }

    public CourseStudentsView() {
    }

    @Immutable
    @Embeddable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class CourseStudentViewId implements Serializable {

        @Column(name = "COURSE_ID")
        private long course_id;

        @Column(name = "STUDENT_ID")
        private long studentId;

        public CourseStudentViewId() {

        }

        public CourseStudentViewId(long course_id, long studentId) {
            this.course_id = course_id;
            this.studentId = studentId;
        }

        public long getCourse_id() {
            return course_id;
        }

        public void setCourse_id(long course_id) {
            this.course_id = course_id;
        }

        public long getStudentId() {
            return studentId;
        }

        public void setStudentId(long exerciseId) {
            this.studentId = exerciseId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            CourseStudentViewId that = (CourseStudentViewId) o;

            if (course_id != that.course_id)
                return false;
            return studentId == that.studentId;
        }

        @Override
        public int hashCode() {
            int result = (int) (course_id ^ (course_id >>> 32));
            result = 31 * result + (int) (studentId ^ (studentId >>> 32));
            return result;
        }
    }
}
