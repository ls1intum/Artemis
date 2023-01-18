package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;
import javax.persistence.*;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "build_plan", uniqueConstraints = { @UniqueConstraint(columnNames = { "build_plan" }) })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BuildPlan extends DomainObject {

    @Size(max = 10_000)
    @Nullable
    @Column(name = "build_plan", table = "build_plan", length = 10_000)
    private String buildPlan;

    @OneToMany
    private Set<ProgrammingExercise> programmingExercises = new HashSet<>();

    public String getBuildPlan() {
        return buildPlan;
    }

    public void setBuildPlan(String buildPlan) {
        this.buildPlan = buildPlan;
    }

    public void addProgrammingExercise(ProgrammingExercise exercise) {
        programmingExercises.add(exercise);
    }
}
