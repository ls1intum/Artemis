package de.tum.in.www1.artemis.domain.team;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;

@Entity
@DiscriminatorValue(value = "CT")
public class CourseTeam extends Team {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    private Course course;

    public Course getCourse() {
        return course;
    }

    public CourseTeam course(Course course) {
        this.course = course;
        return this;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Team addStudents(User user) {
        CourseTeamStudent courseTeamStudent = new CourseTeamStudent(this, user);
        this.students.add(courseTeamStudent);
        return this;
    }

    public String toString() {
        return "CourseTeam{" + "id=" + getId() + ", course=" + course + '}';
    }
}
