package de.tum.in.www1.artemis.domain.localci;

import java.util.*;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;

@Entity
@Table(name = "local_ci_build_plan")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LocalCIBuildPlan extends DomainObject {

    @Column(name = "name", table = "local_ci_build_plan")
    private String name;

    @OneToMany(mappedBy = "localCIBuildPlan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties(value = "localCIBuildPlan", allowSetters = true)
    private Set<ProgrammingExercise> programmingExercises = new HashSet<>();

    @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinTable(name = "local_ci_build_plan_stages", joinColumns = @JoinColumn(name = "local_ci_build_plan_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "local_ci_build_stage_id", referencedColumnName = "id"))
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties(value = "buildPlans")
    @OrderColumn(name = "stage_order")
    private List<LocalCIBuildStage> stages = new ArrayList<>();

    @Column(name = "docker_image", table = "local_ci_build_plan")
    private String dockerImage;

    public String getName() {
        return name;
    }

    public Optional<ProgrammingExercise> getProgrammingExerciseById(Long exerciseId) {
        return programmingExercises.stream().filter(programmingExercise -> programmingExercise.getId() == exerciseId).findFirst();
    }

    public Optional<LocalCIBuildStage> getStagebyId(Long stageId) {
        return stages.stream().filter(stage -> stage.getId() == stageId).findFirst();
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDockerImage(String dockerImage) {
        this.dockerImage = dockerImage;
    }

    public void addProgrammingExercise(ProgrammingExercise exercise) {
        this.programmingExercises.add(exercise);
    }

    public void removeProgrammingExercise(ProgrammingExercise exercise) {
        this.programmingExercises.remove(exercise);
    }
}
