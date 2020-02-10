package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.validation.constraints.TeamAssignmentConfigConstraints;

/**
 * A team assignment configuration.
 */
@Entity
@Table(name = "team_assignment_config")
@TeamAssignmentConfigConstraints
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TeamAssignmentConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(mappedBy = "teamAssignmentConfig", fetch = FetchType.LAZY, optional = false)
    @JsonIgnoreProperties("teamAssignmentConfig")
    private Exercise exercise;

    @Min(1)
    @NotNull
    @Column(name = "min_team_size")
    private Integer minTeamSize;

    @Min(1)
    @Column(name = "max_team_size")
    private Integer maxTeamSize;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public TeamAssignmentConfig exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Integer getMinTeamSize() {
        return minTeamSize;
    }

    public TeamAssignmentConfig minTeamSize(Integer minTeamSize) {
        this.minTeamSize = minTeamSize;
        return this;
    }

    public void setMinTeamSize(Integer minTeamSize) {
        this.minTeamSize = minTeamSize;
    }

    public Integer getMaxTeamSize() {
        return maxTeamSize;
    }

    public TeamAssignmentConfig maxTeamSize(Integer maxTeamSize) {
        this.maxTeamSize = maxTeamSize;
        return this;
    }

    public void setMaxTeamSize(Integer maxTeamSize) {
        this.maxTeamSize = maxTeamSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TeamAssignmentConfig team = (TeamAssignmentConfig) o;
        if (team.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), team.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "TeamAssignmentConfig{" + "id=" + getId() + ", minTeamSize='" + getMinTeamSize() + "'" + ", maxTeamSize='" + getMaxTeamSize() + "'" + "}";
    }
}
