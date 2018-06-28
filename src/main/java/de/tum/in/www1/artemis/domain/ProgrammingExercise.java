package de.tum.in.www1.artemis.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * A ProgrammingExercise.
 */
@Entity
@Table(name = "programming_exercise")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ProgrammingExercise implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_repository_url")
    private String baseRepositoryUrl;

    @Column(name = "base_build_plan_id")
    private String baseBuildPlanId;

    @Column(name = "publish_build_plan_url")
    private Boolean publishBuildPlanUrl;

    @Column(name = "allow_online_editor")
    private Boolean allowOnlineEditor;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBaseRepositoryUrl() {
        return baseRepositoryUrl;
    }

    public ProgrammingExercise baseRepositoryUrl(String baseRepositoryUrl) {
        this.baseRepositoryUrl = baseRepositoryUrl;
        return this;
    }

    public void setBaseRepositoryUrl(String baseRepositoryUrl) {
        this.baseRepositoryUrl = baseRepositoryUrl;
    }

    public String getBaseBuildPlanId() {
        return baseBuildPlanId;
    }

    public ProgrammingExercise baseBuildPlanId(String baseBuildPlanId) {
        this.baseBuildPlanId = baseBuildPlanId;
        return this;
    }

    public void setBaseBuildPlanId(String baseBuildPlanId) {
        this.baseBuildPlanId = baseBuildPlanId;
    }

    public Boolean isPublishBuildPlanUrl() {
        return publishBuildPlanUrl;
    }

    public ProgrammingExercise publishBuildPlanUrl(Boolean publishBuildPlanUrl) {
        this.publishBuildPlanUrl = publishBuildPlanUrl;
        return this;
    }

    public void setPublishBuildPlanUrl(Boolean publishBuildPlanUrl) {
        this.publishBuildPlanUrl = publishBuildPlanUrl;
    }

    public Boolean isAllowOnlineEditor() {
        return allowOnlineEditor;
    }

    public ProgrammingExercise allowOnlineEditor(Boolean allowOnlineEditor) {
        this.allowOnlineEditor = allowOnlineEditor;
        return this;
    }

    public void setAllowOnlineEditor(Boolean allowOnlineEditor) {
        this.allowOnlineEditor = allowOnlineEditor;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProgrammingExercise programmingExercise = (ProgrammingExercise) o;
        if (programmingExercise.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), programmingExercise.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ProgrammingExercise{" +
            "id=" + getId() +
            ", baseRepositoryUrl='" + getBaseRepositoryUrl() + "'" +
            ", baseBuildPlanId='" + getBaseBuildPlanId() + "'" +
            ", publishBuildPlanUrl='" + isPublishBuildPlanUrl() + "'" +
            ", allowOnlineEditor='" + isAllowOnlineEditor() + "'" +
            "}";
    }
}
