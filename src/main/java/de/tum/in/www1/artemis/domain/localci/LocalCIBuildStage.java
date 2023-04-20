package de.tum.in.www1.artemis.domain.localci;

import java.util.*;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "local_ci_build_stage")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LocalCIBuildStage extends DomainObject {

    @Column(name = "name", table = "local_ci_build_stage")
    private String name;

    @ManyToMany(mappedBy = "stages")
    private Set<LocalCIBuildPlan> buildPlans = new HashSet<>();

    @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinTable(name = "local_ci_build_stage_tasks", joinColumns = @JoinColumn(name = "local_ci_build_stage_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "local_ci_build_task_id", referencedColumnName = "id"))
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("stages")
    @OrderColumn(name = "task_order")
    private List<LocalCIBuildTask> tasks = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Optional<LocalCIBuildTask> getTaskById(Long taskId) {
        return tasks.stream().filter(task -> task.getId().equals(taskId)).findFirst();
    }

    public Optional<LocalCIBuildPlan> getBuildPlanById(Long buildPlanId) {
        return buildPlans.stream().filter(buildPlan -> buildPlan.getId().equals(buildPlanId)).findFirst();
    }

    public void addBuildPlan(LocalCIBuildPlan buildPlan) {
        buildPlans.add(buildPlan);
    }

    public void removeBuildPlan(LocalCIBuildPlan buildPlan) {
        buildPlans.remove(buildPlan);
    }

}
