package de.tum.in.www1.artemis.domain.team;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class TeamStudentId implements Serializable {

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "student_id")
    private Long studentId;

    public TeamStudentId() {
    }

    public TeamStudentId(Long teamId, Long studentId) {
        this.teamId = teamId;
        this.studentId = studentId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public Long getStudentId() {
        return studentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TeamStudentId))
            return false;
        TeamStudentId that = (TeamStudentId) o;
        return Objects.equals(getTeamId(), that.getTeamId()) && Objects.equals(getStudentId(), that.getStudentId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTeamId(), getStudentId());
    }
}
