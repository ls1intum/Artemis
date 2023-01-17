package de.tum.in.www1.artemis.domain;

import javax.annotation.Nullable;
import javax.persistence.*;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@Entity
@Table(name = "build_plan", uniqueConstraints = { @UniqueConstraint(columnNames = { "build_plan" }) })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BuildPlan extends DomainObject {

    @Size(max = 10_000)
    @Nullable
    @Column(name = "build_plan", table = "build_plan", length = 10_000)
    private String buildPlan;

    @OneToMany(mappedBy = "buildPlan")
    private List<ProgrammingExercise> programmingExercises;

    public BuildPlan() {
    }

    public BuildPlan(String buildPlan) {
        this.buildPlan = buildPlan;
    }

    public String getBuildPlan() {
        return buildPlan;
    }

    public void setBuildPlan(String buildPlan) {
        this.buildPlan = buildPlan;
    }

    public List<ProgrammingExercise> getProgrammingExercises() {
        return programmingExercises;
    }

    public void setProgrammingExercises(List<ProgrammingExercise> programmingExercises) {
        this.programmingExercises = programmingExercises;
    }
}
