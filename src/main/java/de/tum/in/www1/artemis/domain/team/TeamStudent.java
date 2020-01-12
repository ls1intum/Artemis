package de.tum.in.www1.artemis.domain.team;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.User;

@Entity
@Table(name = "team_student", uniqueConstraints = { @UniqueConstraint(columnNames = { "student_id", "exercise_id" }),
        @UniqueConstraint(columnNames = { "student_id", "course_id" }) })
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "TS")
public abstract class TeamStudent implements Serializable {

    @EmbeddedId
    private TeamStudentId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("teamId")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("studentId")
    private User student;

    public TeamStudent() {
    }

    public TeamStudent(Team team, User student) {
        this.team = team;
        this.student = student;
        this.id = new TeamStudentId(team.getId(), student.getId());
    }

    public TeamStudentId getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public TeamStudent team(Team team) {
        this.team = team;
        return this;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public User getStudent() {
        return student;
    }

    public TeamStudent student(User user) {
        this.student = user;
        return this;
    }

    public void setStudent(User user) {
        this.student = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TeamStudent))
            return false;
        TeamStudent that = (TeamStudent) o;
        return Objects.equals(getTeam(), that.getTeam()) && Objects.equals(getStudent(), that.getStudent());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTeam(), getStudent());
    }
}
