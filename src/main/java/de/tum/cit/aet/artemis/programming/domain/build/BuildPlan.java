package de.tum.cit.aet.artemis.programming.domain.build;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "build_plan", uniqueConstraints = { @UniqueConstraint(columnNames = { "build_plan" }) })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BuildPlan extends DomainObject {

    @Size(max = 10_000)
    @Nullable
    @Column(name = "build_plan", table = "build_plan", length = 10_000)
    private String buildPlan;

    @OneToMany
    @JoinColumn(name = "build_plan_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<ProgrammingExercise> programmingExercises = new HashSet<>();

    @Nullable
    public String getBuildPlan() {
        return buildPlan;
    }

    public void setBuildPlan(@Nullable String buildPlan) {
        this.buildPlan = buildPlan;
    }

    public void addProgrammingExercise(ProgrammingExercise exercise) {
        programmingExercises.add(exercise);
    }

    public Optional<ProgrammingExercise> getProgrammingExerciseById(Long exerciseId) {
        return programmingExercises.stream().filter(programmingExercise -> Objects.equals(programmingExercise.getId(), exerciseId)).findFirst();
    }
}
