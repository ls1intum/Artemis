package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

// TODO: we should convert this into a record to make objects immutable
public class LocalCIBuildJobQueueItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long id;

    private final String name;

    private String buildAgentAddress;

    private final long participationId;

    private final String repositoryTypeOrUserName;

    private final String commitHash;

    private final ZonedDateTime submissionDate;

    private int retryCount;

    private ZonedDateTime buildStartDate;

    // 1-5, 1 is highest priority
    private final int priority;

    private final long courseId;

    private final boolean isPushToTestRepository;

    public LocalCIBuildJobQueueItem(String name, long participationId, String repositoryTypeOrUserName, String commitHash, ZonedDateTime submissionDate, int priority,
            long courseId, boolean isPushToTestRepository) {
        this.id = Long.parseLong(String.valueOf(participationId) + submissionDate.toInstant().toEpochMilli());
        this.name = name;
        this.participationId = participationId;
        this.repositoryTypeOrUserName = repositoryTypeOrUserName;
        this.commitHash = commitHash;
        this.submissionDate = submissionDate;
        this.priority = priority;
        this.courseId = courseId;
        this.isPushToTestRepository = isPushToTestRepository;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBuildAgentAddress() {
        return buildAgentAddress;
    }

    public void setBuildAgentAddress(String buildAgentAddress) {
        this.buildAgentAddress = buildAgentAddress;
    }

    public long getParticipationId() {
        return participationId;
    }

    public String getRepositoryTypeOrUserName() {
        return repositoryTypeOrUserName;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public ZonedDateTime getSubmissionDate() {
        return submissionDate;
    }

    public ZonedDateTime getBuildStartDate() {
        return buildStartDate;
    }

    public void setBuildStartDate(ZonedDateTime buildStartDate) {
        this.buildStartDate = buildStartDate;
    }

    public int getPriority() {
        return priority;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public long getCourseId() {
        return courseId;
    }

    /**
     * When pushing to the test repository, build jobs are triggered for the template and solution repository.
     * However, getCommitHash() then returns the commit hash of the test repository.
     * This flag is necessary so we do not try to checkout the commit hash of the test repository in the template/solution repository.
     *
     * @return true if the build job was triggered by a push to the test repository
     */
    public boolean isPushToTestRepository() {
        return isPushToTestRepository;
    }

    @Override
    public String toString() {
        return "LocalCIBuildJobQueueItem{" + "id='" + id + '\'' + ", name='" + name + '\'' + ", participationId=" + participationId + ", repositoryTypeOrUserName='"
                + repositoryTypeOrUserName + '\'' + ", commitHash='" + commitHash + '\'' + ", submissionDate=" + submissionDate + ", retryCount=" + retryCount + ", buildStartDate="
                + buildStartDate + ", priority=" + priority + ", courseId=" + courseId + ", isPushToTestRepository=" + isPushToTestRepository + '}';
    }
}
