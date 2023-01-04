package de.tum.in.www1.artemis.domain;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
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
}
