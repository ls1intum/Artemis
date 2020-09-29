package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A ProgrammingSubmission.
 */
@Entity
@DiscriminatorValue(value = "P")
public class ProgrammingSubmission extends Submission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "commit_hash")
    private String commitHash;

    @Column(name = "build_failed")
    private boolean buildFailed;

    @Column(name = "build_artifact")
    private boolean buildArtifact;

    // Only present if buildFailed == true
    @OneToMany(mappedBy = "programmingSubmission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn
    @JsonIgnoreProperties(value = "programmingSubmission", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<BuildLogEntry> buildLogEntries = new ArrayList<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

    public String getCommitHash() {
        return commitHash;
    }

    public ProgrammingSubmission commitHash(String commitHash) {
        this.commitHash = commitHash;
        return this;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    public boolean isBuildFailed() {
        return buildFailed;
    }

    public void setBuildFailed(boolean buildFailed) {
        this.buildFailed = buildFailed;
    }

    public boolean isBuildArtifact() {
        return buildArtifact;
    }

    public void setBuildArtifact(boolean buildArtifact) {
        this.buildArtifact = buildArtifact;
    }

    public List<BuildLogEntry> getBuildLogEntries() {
        return buildLogEntries;
    }

    public void setBuildLogEntries(List<BuildLogEntry> buildLogEntries) {
        this.buildLogEntries = buildLogEntries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ProgrammingSubmission))
            return false;
        if (!super.equals(o))
            return false;
        ProgrammingSubmission that = (ProgrammingSubmission) o;
        return buildFailed == that.buildFailed && buildArtifact == that.buildArtifact && Objects.equals(commitHash, that.commitHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), commitHash, buildFailed, buildArtifact);
    }

    @Override
    public String toString() {
        return "ProgrammingSubmission{" + "commitHash='" + commitHash + '\'' + ", buildFailed=" + buildFailed + ", buildArtifact=" + buildArtifact + '}';
    }
}
