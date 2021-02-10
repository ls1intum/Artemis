package de.tum.in.www1.artemis.domain;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;

/**
 * A ProgrammingSubmission.
 */
@Entity
@DiscriminatorValue(value = "P")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingSubmission extends Submission {

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

    public boolean belongsToTestRepository() {
        return SubmissionType.TEST.equals(getType());
    }

    @Override
    public boolean isEmpty() {
        return false; // programming submissions cannot be empty, they are only created for actual commits in the git repository
    }

    @Override
    public String toString() {
        return "ProgrammingSubmission{" + "commitHash='" + commitHash + '\'' + ", buildFailed=" + buildFailed + ", buildArtifact=" + buildArtifact + '}';
    }
}
