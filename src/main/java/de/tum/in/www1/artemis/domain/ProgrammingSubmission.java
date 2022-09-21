package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.statistics.BuildLogStatisticsEntry;

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

    @OneToOne(mappedBy = "programmingSubmission", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties(value = "programmingSubmission", allowSetters = true)
    @JoinColumn(unique = true)
    private BuildLogStatisticsEntry buildLogStatisticsEntry;

    /**
     * There can be two reasons for the case that there is no programmingSubmission:
     * 1) Manual build triggered from CI (e.g. by the instructor)
     * 2) An unknown error that caused the programming submission not to be created when the code commits have been pushed.
     * we can still get the commit hash from the payload of the CI build result and "reverse engineer" the programming submission object to be consistent
     *
     * @param participation the corresponding participation to which the submission will correspond
     * @param submissionDate the date when the commit was pushed to the version control server
     * @param commitHash the hash of the corresponding commit in the git repository in the version control system
     * @return the newly created programming submission
     *
     */
    @NotNull
    public static ProgrammingSubmission createFallbackSubmission(ProgrammingExerciseParticipation participation, ZonedDateTime submissionDate, String commitHash) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setParticipation((Participation) participation);
        submission.setSubmitted(true);
        // We set this to manual because all programming submissions should correspond to a student commit in the git history.
        // In case we cannot find the appropriate submission, it means something has not worked before, but there will still be a commit in the student repository
        submission.setType(SubmissionType.MANUAL);
        submission.setCommitHash(commitHash);
        submission.setSubmissionDate(submissionDate);
        return submission;
    }

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

    public BuildLogStatisticsEntry getBuildLogStatisticsEntry() {
        return buildLogStatisticsEntry;
    }

    public void setBuildLogStatisticsEntry(BuildLogStatisticsEntry buildLogStatisticsEntry) {
        this.buildLogStatisticsEntry = buildLogStatisticsEntry;
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

    @Override
    public int compareTo(Submission other) {
        if (getSubmissionDate() == null || other.getSubmissionDate() == null
                || other instanceof ProgrammingSubmission otherProgrammingSubmission && Objects.equals(getCommitHash(), otherProgrammingSubmission.getCommitHash())) {
            // this case should not happen, but in the rare case we can compare the ids
            // newer ids are typically later
            return getId().compareTo(other.getId());
        }
        return getSubmissionDate().compareTo(other.getSubmissionDate());
    }
}
