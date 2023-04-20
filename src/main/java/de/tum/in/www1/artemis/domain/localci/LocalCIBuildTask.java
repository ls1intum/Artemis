package de.tum.in.www1.artemis.domain.localci;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "local_ci_build_task")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LocalCIBuildTask extends DomainObject {

    @Column(name = "name", table = "local_ci_build_task")
    private String name;

    @ManyToMany(mappedBy = "tasks")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<LocalCIBuildStage> stages = new HashSet<>();

    @Column(name = "script", table = "local_ci_build_task")
    private String script;

    public String getName() {
        return name;
    }

    public String getScript() {
        return script;
    }

    public Optional<LocalCIBuildStage> getStageById(Long stageId) {
        return stages.stream().filter(stage -> stage.getId().equals(stageId)).findFirst();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public void addStage(LocalCIBuildStage stage) {
        stages.add(stage);
    }

    public void removeStage(LocalCIBuildStage stage) {
        stages.remove(stage);
    }
}
