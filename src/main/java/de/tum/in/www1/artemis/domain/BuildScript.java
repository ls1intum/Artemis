package de.tum.in.www1.artemis.domain;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "build_script", uniqueConstraints = { @UniqueConstraint(columnNames = { "build_script" }) })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BuildScript extends DomainObject {

    @Column(name = "api", table = "build_script")
    private String api;

    @Column(name = "build_script", table = "build_script")
    private String buildScript;

    @OneToOne
    @JoinColumn(name = "programming_exercise_details_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private ProgrammingExercise programmingExercise;

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public ProgrammingExercise getProgrammingExercise() {
        return programmingExercise;
    }

    @Nullable
    public String getBuildScript() {
        return buildScript;
    }

    public void setBuildScript(String buildScript) {
        this.buildScript = buildScript;
    }

    public void setProgrammingExercise(ProgrammingExercise exercise) {
        this.programmingExercise = exercise;
    }

}
