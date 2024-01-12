package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.BuildJobResult;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;

@Entity
@Table(name = "build_job")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BuildJob extends DomainObject {

    @Column(name = "name")
    private String name;

    @Column(name = "exercise_id")
    private Long exerciseId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "participation_id")
    private Long participationId;

    @Column(name = "build_agent_address")
    private String buildAgentAddress;

    @Column(name = "build_start_date")
    private ZonedDateTime buildStartDate;

    @Column(name = "build_completion_date")
    private ZonedDateTime buildCompletionDate;

    @Column(name = "repository_type")
    private RepositoryType repositoryType;

    @Column(name = "repository_name")
    private String repositoryName;

    @Column(name = "commit_hash")
    private String commitHash;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "priority")
    private int priority;

    @Column(name = "triggered_by_push_to")
    private RepositoryType triggeredByPushTo;

    @Column(name = "build_job_result")
    private BuildJobResult buildJobResult;

    @Column(name = "docker_image")
    private String dockerImage;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getParticipationId() {
        return participationId;
    }

    public void setParticipationId(Long participationId) {
        this.participationId = participationId;
    }

    public String getBuildAgentAddress() {
        return buildAgentAddress;
    }

    public void setBuildAgentAddress(String buildAgentAddress) {
        this.buildAgentAddress = buildAgentAddress;
    }

    public ZonedDateTime getBuildStartDate() {
        return buildStartDate;
    }

    public void setBuildStartDate(ZonedDateTime buildStartDate) {
        this.buildStartDate = buildStartDate;
    }

    public ZonedDateTime getBuildCompletionDate() {
        return buildCompletionDate;
    }

    public void setBuildCompletionDate(ZonedDateTime buildCompletionDate) {
        this.buildCompletionDate = buildCompletionDate;
    }

    public RepositoryType getRepositoryType() {
        return repositoryType;
    }

    public void setRepositoryType(RepositoryType repositoryType) {
        this.repositoryType = repositoryType;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public RepositoryType getTriggeredByPushTo() {
        return triggeredByPushTo;
    }

    public void setTriggeredByPushTo(RepositoryType triggeredByPushTo) {
        this.triggeredByPushTo = triggeredByPushTo;
    }

    public BuildJobResult getBuildJobResult() {
        return buildJobResult;
    }

    public void setBuildJobResult(BuildJobResult buildJobResult) {
        this.buildJobResult = buildJobResult;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public void setDockerImage(String dockerImage) {
        this.dockerImage = dockerImage;
    }
}
