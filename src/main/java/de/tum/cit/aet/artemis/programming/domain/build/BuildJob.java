package de.tum.cit.aet.artemis.programming.domain.build;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;

@Entity
@Table(name = "build_job")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BuildJob extends DomainObject {

    @Column(name = "build_job_id")
    private String buildJobId;

    @Column(name = "name")
    private String name;

    @Column(name = "exercise_id")
    private Long exerciseId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "participation_id")
    private Long participationId;

    @Column(name = "container_id")
    private Long containerId;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(
        name = "build_job_result",
        joinColumns = @JoinColumn(name = "build_job_id"),
        inverseJoinColumns = @JoinColumn(name = "result_id")
    )
    private List<Result> results = new ArrayList<>();

    @Column(name = "build_agent_address")
    private String buildAgentAddress;

    @Column(name = "build_submission_date")
    private ZonedDateTime buildSubmissionDate;

    @Column(name = "build_start_date")
    private ZonedDateTime buildStartDate;

    @Column(name = "build_completion_date")
    private ZonedDateTime buildCompletionDate;

    @Enumerated(EnumType.STRING)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by_push_to")
    private RepositoryType triggeredByPushTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "build_job_result")
    private BuildStatus buildStatus;

    @Column(name = "docker_image")
    private String dockerImage;

    public BuildJob() {
    }

    public BuildJob(BuildJobQueueItem queueItem, BuildStatus buildStatus, Result result) {
        this(queueItem, buildStatus, List.of(result));
    }

    public BuildJob(BuildJobQueueItem queueItem, BuildStatus buildStatus, List<Result> results) {
        this.buildJobId = queueItem.id();
        this.name = queueItem.name();
        this.exerciseId = queueItem.exerciseId();
        this.courseId = queueItem.courseId();
        this.participationId = queueItem.participationId();
        this.containerId = queueItem.containerId();
        this.results = new ArrayList<>(results);
        this.buildAgentAddress = queueItem.buildAgent().memberAddress();
        this.buildSubmissionDate = queueItem.jobTimingInfo().submissionDate();
        this.buildStartDate = queueItem.jobTimingInfo().buildStartDate();
        this.buildCompletionDate = queueItem.jobTimingInfo().buildCompletionDate();
        this.repositoryType = queueItem.repositoryInfo().repositoryType();
        this.repositoryName = queueItem.repositoryInfo().repositoryName();
        this.commitHash = queueItem.buildConfig().commitHashToBuild();
        this.retryCount = queueItem.retryCount();
        this.priority = queueItem.priority();
        this.triggeredByPushTo = queueItem.repositoryInfo().triggeredByPushTo();
        this.buildStatus = buildStatus;
        this.dockerImage = queueItem.buildConfig().dockerImage();
    }

    public String getBuildJobId() {
        return buildJobId;
    }

    public void setBuildJobId(String buildJobId) {
        this.buildJobId = buildJobId;
    }

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

    public Long getContainerId() {
        return containerId;
    }

    public void setContainerId(Long containerId) {
        this.containerId = containerId;
    }

    public List<Result> getResults() {
        return results;
    }

    public void setResults(List<Result> results) {
        this.results = results;
    }

    public String getBuildAgentAddress() {
        return buildAgentAddress;
    }

    public void setBuildAgentAddress(String buildAgentAddress) {
        this.buildAgentAddress = buildAgentAddress;
    }

    public ZonedDateTime getBuildSubmissionDate() {
        return buildSubmissionDate;
    }

    public void setBuildSubmissionDate(ZonedDateTime buildSubmissionDate) {
        this.buildSubmissionDate = buildSubmissionDate;
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

    public BuildStatus getBuildStatus() {
        return buildStatus;
    }

    public void setBuildStatus(BuildStatus buildStatus) {
        this.buildStatus = buildStatus;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public void setDockerImage(String dockerImage) {
        this.dockerImage = dockerImage;
    }

    @Override
    public String toString() {
        return "BuildJob{" + "buildJobId='" + buildJobId + "'" + ", name='" + name + "'" + ", exerciseId=" + exerciseId + ", courseId=" + courseId + ", participationId="
                + participationId + ", buildAgentAddress='" + buildAgentAddress + "'" + ", buildStartDate=" + buildStartDate + ", buildCompletionDate=" + buildCompletionDate
                + ", repositoryType=" + repositoryType + ", repositoryName='" + repositoryName + "'" + ", commitHash='" + commitHash + "'" + ", retryCount=" + retryCount
                + ", priority=" + priority + ", triggeredByPushTo=" + triggeredByPushTo + ", buildStatus=" + buildStatus + ", dockerImage='" + dockerImage + "'" + "}";
    }
}
