package de.tum.in.www1.artemis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.validation.constraints.TeamAssignmentConfigConstraints;

/**
 * A team assignment configuration.
 */
@Entity
@Table(name = "team_assignment_config")
@TeamAssignmentConfigConstraints
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TeamAssignmentConfig extends DomainObject {

    @OneToOne(mappedBy = "teamAssignmentConfig", fetch = FetchType.LAZY)
    @JsonIgnoreProperties("teamAssignmentConfig")
    private Exercise exercise;

    @Min(1)
    @NonNull
    @Column(name = "min_team_size")
    private Integer minTeamSize;

    @Min(1)
    @Column(name = "max_team_size")
    private Integer maxTeamSize;

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Integer getMinTeamSize() {
        return minTeamSize;
    }

    public void setMinTeamSize(Integer minTeamSize) {
        this.minTeamSize = minTeamSize;
    }

    public Integer getMaxTeamSize() {
        return maxTeamSize;
    }

    public void setMaxTeamSize(Integer maxTeamSize) {
        this.maxTeamSize = maxTeamSize;
    }

    @Override
    public String toString() {
        return "TeamAssignmentConfig{" + "id=" + getId() + ", minTeamSize='" + getMinTeamSize() + "'" + ", maxTeamSize='" + getMaxTeamSize() + "'" + "}";
    }

    /**
     * Helper method which does a hard copy of the Team Assignment Configurations.
     *
     * @return The cloned configuration
     */
    public TeamAssignmentConfig copyTeamAssignmentConfig() {
        TeamAssignmentConfig newConfig = new TeamAssignmentConfig();
        newConfig.setMinTeamSize(getMinTeamSize());
        newConfig.setMaxTeamSize(getMaxTeamSize());
        return newConfig;
    }
}
