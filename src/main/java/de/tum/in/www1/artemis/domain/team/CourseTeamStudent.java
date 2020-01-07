package de.tum.in.www1.artemis.domain.team;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;

@Entity
@DiscriminatorValue(value = "CTS")
public class CourseTeamStudent extends TeamStudent {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    private Course course;

    public CourseTeamStudent() {
        super();
    }

    public CourseTeamStudent(CourseTeam courseTeam, User user) {
        super(courseTeam, user);
        this.course = courseTeam.getCourse();
    }

    public String toString() {
        return "CourseTeamStudent{" + "id=" + getId() + ", course=" + course + ", team=" + getTeam() + ", student=" + getStudent() + '}';
    }
}
