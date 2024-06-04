package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import java.net.URL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GitLabProjectDTO(int id, String name, String description, @JsonProperty("web_url") URL webUrl, @JsonProperty("git_ssh_url") String sshUrl,
        @JsonProperty("git_http_url") URL httpUrl, String namespace, @JsonProperty("visibility_level") int visibility, @JsonProperty("path_with_namespace") String namespacePath,
        @JsonProperty("default_branch") String defaultBranch) {
}
