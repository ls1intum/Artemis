package de.tum.in.www1.artemis.service.connectors.gitlab.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GitLabPushNotification {

    @JsonProperty("object_kind")
    private String triggerType;

    @JsonProperty("event_name")
    private String eventName;

    @JsonProperty("before")
    private String previousHash;

    @JsonProperty("after")
    private String newHash;

    private String ref;

    @JsonProperty("checkout_sha")
    private String commitHash;

    private String message;

    @JsonProperty("user_id")
    private int userId;

    @JsonProperty("user_name")
    private String userFullName;

    @JsonProperty("user_username")
    private String username;

    @JsonProperty("user_email")
    private String userMail;

    @JsonProperty("project_id")
    private int projectId;

    private GitLabProject project;

    private List<GitLabCommit> commits;

    @JsonProperty("total_commits_count")
    private int totalCommitsCount;

    private GitLabRepository repository;

    public static GitLabPushNotification convert(Object someNotification) {
        return new ObjectMapper().registerModule(new JavaTimeModule()).convertValue(someNotification, GitLabPushNotification.class);
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public String getNewHash() {
        return newHash;
    }

    public void setNewHash(String newHash) {
        this.newHash = newHash;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUserFullName() {
        return userFullName;
    }

    public void setUserFullName(String userFullName) {
        this.userFullName = userFullName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserMail() {
        return userMail;
    }

    public void setUserMail(String userMail) {
        this.userMail = userMail;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public GitLabProject getProject() {
        return project;
    }

    public void setProject(GitLabProject project) {
        this.project = project;
    }

    public List<GitLabCommit> getCommits() {
        return commits;
    }

    public void setCommits(List<GitLabCommit> commits) {
        this.commits = commits;
    }

    public int getTotalCommitsCount() {
        return totalCommitsCount;
    }

    public void setTotalCommitsCount(int totalCommitsCount) {
        this.totalCommitsCount = totalCommitsCount;
    }

    public GitLabRepository getRepository() {
        return repository;
    }

    public void setRepository(GitLabRepository repository) {
        this.repository = repository;
    }
}
