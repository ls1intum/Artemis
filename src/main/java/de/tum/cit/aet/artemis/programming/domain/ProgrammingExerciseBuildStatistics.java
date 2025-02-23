package de.tum.cit.aet.artemis.programming.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "programming_exercise_build_statistics")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseBuildStatistics extends DomainObject {

    @Column(name = "build_duration_seconds")
    private long buildDurationSeconds = 0;

    @Column(name = "build_count_when_updated")
    private long buildCountWhenUpdated = 0;

    @Column(name = "exercise_id")
    private Long exerciseId;

    public ProgrammingExerciseBuildStatistics() {
    }

    public ProgrammingExerciseBuildStatistics(Long exerciseId, long buildDurationSeconds, long buildCountWhenUpdated) {
        this.buildDurationSeconds = buildDurationSeconds;
        this.buildCountWhenUpdated = buildCountWhenUpdated;
        this.exerciseId = exerciseId;
    }

    public long getBuildDurationSeconds() {
        return buildDurationSeconds;
    }

    public void setBuildDurationSeconds(long buildDurationSeconds) {
        this.buildDurationSeconds = buildDurationSeconds;
    }

    public long getBuildCountWhenUpdated() {
        return buildCountWhenUpdated;
    }

    public void setBuildCountWhenUpdated(long buildCountWhenUpdated) {
        this.buildCountWhenUpdated = buildCountWhenUpdated;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }
}
