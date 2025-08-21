package de.tum.cit.aet.artemis.jenkins.connector.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity representing a Jenkins project/folder mapping.
 * Maps Artemis exercise information to Jenkins project structure.
 */
@Entity
@Table(name = "jenkins_projects")
public class JenkinsProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exercise_id", unique = true, nullable = false)
    private Long exerciseId;

    @Column(name = "project_key", nullable = false)
    private String projectKey;

    @Column(name = "jenkins_folder_name", nullable = false)
    private String jenkinsFolderName;

    @Column(name = "programming_language", nullable = false)
    private String programmingLanguage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    public JenkinsProject() {}

    public JenkinsProject(Long exerciseId, String projectKey, String jenkinsFolderName, String programmingLanguage) {
        this.exerciseId = exerciseId;
        this.projectKey = projectKey;
        this.jenkinsFolderName = jenkinsFolderName;
        this.programmingLanguage = programmingLanguage;
        this.createdAt = Instant.now();
        this.lastAccessedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getExerciseId() { return exerciseId; }
    public void setExerciseId(Long exerciseId) { this.exerciseId = exerciseId; }

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

    public String getJenkinsFolderName() { return jenkinsFolderName; }
    public void setJenkinsFolderName(String jenkinsFolderName) { this.jenkinsFolderName = jenkinsFolderName; }

    public String getProgrammingLanguage() { return programmingLanguage; }
    public void setProgrammingLanguage(String programmingLanguage) { this.programmingLanguage = programmingLanguage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(Instant lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
}