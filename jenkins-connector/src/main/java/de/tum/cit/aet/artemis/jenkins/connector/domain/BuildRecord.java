package de.tum.cit.aet.artemis.jenkins.connector.domain;

import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildStatusResponseDTO.BuildStatus;

/**
 * Entity representing a build record in the Jenkins connector.
 */
@Entity
@Table(name = "build_record")
public class BuildRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "build_id", unique = true, nullable = false)
    private UUID buildId;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    @Column(name = "participation_id", nullable = false)
    private Long participationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BuildStatus status;

    @Column(name = "jenkins_job_name")
    private String jenkinsJobName;

    @Column(name = "jenkins_build_number")
    private Integer jenkinsBuildNumber;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @ManyToOne
    @JoinColumn(name = "jenkins_project_id")
    private JenkinsProject jenkinsProject;

    public BuildRecord() {
    }

    public BuildRecord(UUID buildId, Long exerciseId, Long participationId, BuildStatus status) {
        this.buildId = buildId;
        this.exerciseId = exerciseId;
        this.participationId = participationId;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getBuildId() {
        return buildId;
    }

    public void setBuildId(UUID buildId) {
        this.buildId = buildId;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Long getParticipationId() {
        return participationId;
    }

    public void setParticipationId(Long participationId) {
        this.participationId = participationId;
    }

    public BuildStatus getStatus() {
        return status;
    }

    public void setStatus(BuildStatus status) {
        this.status = status;
    }

    public String getJenkinsJobName() {
        return jenkinsJobName;
    }

    public void setJenkinsJobName(String jenkinsJobName) {
        this.jenkinsJobName = jenkinsJobName;
    }

    public Integer getJenkinsBuildNumber() {
        return jenkinsBuildNumber;
    }

    public void setJenkinsBuildNumber(Integer jenkinsBuildNumber) {
        this.jenkinsBuildNumber = jenkinsBuildNumber;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public JenkinsProject getJenkinsProject() {
        return jenkinsProject;
    }

    public void setJenkinsProject(JenkinsProject jenkinsProject) {
        this.jenkinsProject = jenkinsProject;
    }
}