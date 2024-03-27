package de.tum.in.www1.artemis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "build_log_result_mapping")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BuildJobResultMapping extends DomainObject {

    @Column(name = "build_job_id")
    private String buildJobId;

    @Column(name = "result_id")
    private Long resultId;

    public BuildJobResultMapping() {
    }

    public BuildJobResultMapping(String buildJobId, Long resultId) {
        this.buildJobId = buildJobId;
        this.resultId = resultId;
    }

    public String getBuildJobId() {
        return buildJobId;
    }

    public void setBuildJobId(String buildJobId) {
        this.buildJobId = buildJobId;
    }

    public Long getResultId() {
        return resultId;
    }

    public void setResultId(Long resultId) {
        this.resultId = resultId;
    }
}
