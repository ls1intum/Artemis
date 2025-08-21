package de.tum.cit.aet.artemis.jenkins.connector.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity representing a build record in the Jenkins connector.
 * Stores mapping between Artemis build UUIDs and Jenkins job information.
 * Build status is always fetched live from Jenkins.
 */
@Entity
@Table(name = "build_records")
public class BuildRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "build_uuid", unique = true, nullable = false)
    private UUID buildUuid;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    @Column(name = "participation_id", nullable = false)
    private Long participationId;

    @Column(name = "jenkins_job_name", nullable = false)
    private String jenkinsJobName;

    @Column(name = "jenkins_build_number")
    private Integer jenkinsBuildNumber;

    @Column(name = "commit_hash")
    private String commitHash;

    @Column(name = "programming_language")
    private String programmingLanguage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public BuildRecord() {}

    public BuildRecord(UUID buildUuid, Long exerciseId, Long participationId, String jenkinsJobName, String programmingLanguage) {
        this.buildUuid = buildUuid;
        this.exerciseId = exerciseId;
        this.participationId = participationId;
        this.jenkinsJobName = jenkinsJobName;
        this.programmingLanguage = programmingLanguage;
        this.createdAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getBuildUuid() { return buildUuid; }
    public void setBuildUuid(UUID buildUuid) { this.buildUuid = buildUuid; }

    public Long getExerciseId() { return exerciseId; }
    public void setExerciseId(Long exerciseId) { this.exerciseId = exerciseId; }

    public Long getParticipationId() { return participationId; }
    public void setParticipationId(Long participationId) { this.participationId = participationId; }

    public String getJenkinsJobName() { return jenkinsJobName; }
    public void setJenkinsJobName(String jenkinsJobName) { this.jenkinsJobName = jenkinsJobName; }

    public Integer getJenkinsBuildNumber() { return jenkinsBuildNumber; }
    public void setJenkinsBuildNumber(Integer jenkinsBuildNumber) { this.jenkinsBuildNumber = jenkinsBuildNumber; }

    public String getCommitHash() { return commitHash; }
    public void setCommitHash(String commitHash) { this.commitHash = commitHash; }

    public String getProgrammingLanguage() { return programmingLanguage; }
    public void setProgrammingLanguage(String programmingLanguage) { this.programmingLanguage = programmingLanguage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}