package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GitLabPushNotificationDTO(@JsonProperty("object_kind") String triggerType, @JsonProperty("event_name") String eventName, @JsonProperty("before") String previousHash,
        @JsonProperty("after") String newHash, String ref, @JsonProperty("checkout_sha") String commitHash, String message, @JsonProperty("user_id") int userId,
        @JsonProperty("user_name") String userFullName, @JsonProperty("user_username") String username, @JsonProperty("user_email") String userMail,
        @JsonProperty("project_id") int projectId, GitLabProjectDTO project, List<GitLabCommitDTO> commits, @JsonProperty("total_commits_count") int totalCommitsCount,
        GitLabRepositoryDTO repository) {

    public static GitLabPushNotificationDTO convert(Object someNotification) {
        return new ObjectMapper().registerModule(new JavaTimeModule()).convertValue(someNotification, GitLabPushNotificationDTO.class);
    }

}
