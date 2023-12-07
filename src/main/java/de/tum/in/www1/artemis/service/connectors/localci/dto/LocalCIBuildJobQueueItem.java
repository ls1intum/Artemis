package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serial;
import java.io.Serializable;

public class LocalCIBuildJobQueueItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private String name;

    private long participationId;

    private String commitHash;

    private long expirationTime;

    private long submissionDate;

    private int retryCount;

    private long buildStartDate;

    // 1-5, 1 is highest priority
    private int priority;

    private long courseId;

    private boolean isTestPush;

    public LocalCIBuildJobQueueItem(String name, long participationId, String commitHash, long submissionDate, int priority, long courseId, boolean isTestPush) {
        this.id = String.valueOf(participationId) + submissionDate;
        this.name = name;
        this.participationId = participationId;
        this.commitHash = commitHash;
        this.submissionDate = submissionDate;
        this.priority = priority;
        this.courseId = courseId;
        this.isTestPush = isTestPush;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getParticipationId() {
        return participationId;
    }

    public void setParticipationId(long participationId) {
        this.participationId = participationId;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(long expirationTime) {
        this.expirationTime = expirationTime;
    }

    public long getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(long submissionDate) {
        this.submissionDate = submissionDate;
    }

    public long getBuildStartDate() {
        return buildStartDate;
    }

    public void setBuildStartDate(long buildStartDate) {
        this.buildStartDate = buildStartDate;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
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

    public void setCourseId(long courseId) {
        this.courseId = courseId;
    }

    public boolean isTestPush() {
        return isTestPush;
    }

    public void setTestPush(boolean testPush) {
        isTestPush = testPush;
    }

    @Override
    public String toString() {
        return "LocalCIBuildJobQueueItem{" + "name='" + name + '\'' + ", participationId=" + participationId + ", commitHash='" + commitHash + '\'' + ", submissionDate="
                + submissionDate + ", retryCount=" + retryCount + ", priority=" + priority + ", buildStartDate=" + buildStartDate + '}';
    }
}
