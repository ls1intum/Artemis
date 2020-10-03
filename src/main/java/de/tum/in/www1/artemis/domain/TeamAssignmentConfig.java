package de.tum.in.www1.artemis.domain;

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
public class TeamAssignmentConfig extends DomainObject {

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
    public String toString() {
        return "TeamAssignmentConfig{" + "id=" + getId() + ", minTeamSize='" + getMinTeamSize() + "'" + ", maxTeamSize='" + getMaxTeamSize() + "'" + "}";
    }
}
