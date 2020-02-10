package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GitLabPushNotificationDTO {

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

    private GitLabProjectDTO project;

    private List<GitLabCommitDTO> commits;

    @JsonProperty("total_commits_count")
    private int totalCommitsCount;

    private GitLabRepositoryDTO repository;

    public static GitLabPushNotificationDTO convert(Object someNotification) {
        return new ObjectMapper().registerModule(new JavaTimeModule()).convertValue(someNotification, GitLabPushNotificationDTO.class);
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

    public GitLabProjectDTO getProject() {
        return project;
    }

    public void setProject(GitLabProjectDTO project) {
        this.project = project;
    }

    public List<GitLabCommitDTO> getCommits() {
        return commits;
    }

    public void setCommits(List<GitLabCommitDTO> commits) {
        this.commits = commits;
    }

    public int getTotalCommitsCount() {
        return totalCommitsCount;
    }

    public void setTotalCommitsCount(int totalCommitsCount) {
        this.totalCommitsCount = totalCommitsCount;
    }

    public GitLabRepositoryDTO getRepository() {
        return repository;
    }

    public void setRepository(GitLabRepositoryDTO repository) {
        this.repository = repository;
    }
}
