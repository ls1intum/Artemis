package de.tum.cit.aet.artemis.jenkins.connector.domain;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Entity representing a Jenkins project (folder) that contains build jobs.
 */
@Entity
@Table(name = "jenkins_project")
public class JenkinsProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", unique = true, nullable = false)
    private String projectKey;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    @Column(name = "jenkins_folder_name", nullable = false)
    private String jenkinsFolderName;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @OneToMany(mappedBy = "jenkinsProject", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BuildRecord> buildRecords = new ArrayList<>();

    public JenkinsProject() {
    }

    public JenkinsProject(String projectKey, Long exerciseId, String jenkinsFolderName) {
        this.projectKey = projectKey;
        this.exerciseId = exerciseId;
        this.jenkinsFolderName = jenkinsFolderName;
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

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public String getJenkinsFolderName() {
        return jenkinsFolderName;
    }

    public void setJenkinsFolderName(String jenkinsFolderName) {
        this.jenkinsFolderName = jenkinsFolderName;
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

    public List<BuildRecord> getBuildRecords() {
        return buildRecords;
    }

    public void setBuildRecords(List<BuildRecord> buildRecords) {
        this.buildRecords = buildRecords;
    }
}