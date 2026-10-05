package de.tum.cit.aet.artemis.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.exercise.web.TeamAssignmentConfigConstraints;

/**
 * A team assignment configuration.
 */
@Entity
@Table(name = "team_assignment_config")
@TeamAssignmentConfigConstraints
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TeamAssignmentConfig extends DomainObject {

    /**
     * The exercise this configuration belongs to. The key lives here rather than on the exercise: the exercise carries no
     * mapped association to its team assignment configuration, so loading an exercise can never pull this row in, and the
     * configuration cannot outlive the exercise. Read it through {@code TeamAssignmentConfigRepository} where it is needed.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false, unique = true)
    @JsonIgnore
    @Parent
    private Exercise exercise;

    /**
     * The key of {@link #exercise}, read without touching the lazy association, so that the configurations of many
     * exercises can be matched to their exercises after one query. Written through {@link #exercise} only.
     */
    @JsonIgnore
    @Column(name = "exercise_id", insertable = false, updatable = false)
    private Long exerciseId;

    public Long getExerciseId() {
        return exerciseId;
    }

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
