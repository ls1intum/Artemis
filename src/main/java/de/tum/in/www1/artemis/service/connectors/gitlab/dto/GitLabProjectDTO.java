package de.tum.in.www1.artemis.service.connectors.gitlab.dto;

import java.net.URL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GitLabProjectDTO {

    private int id;

    private String name;

    private String description;

    @JsonProperty("web_url")
    private URL webUrl;

    @JsonProperty("git_ssh_url")
    private String sshUrl;

    @JsonProperty("git_http_url")
    private URL httpUrl;

    private String namespace;

    @JsonProperty("visibility_level")
    private int visibility;

    @JsonProperty("path_with_namespace")
    private String namespacePath;

    @JsonProperty("default_branch")
    private String defaultBranch;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public URL getWebUrl() {
        return webUrl;
    }

    public void setWebUrl(URL webUrl) {
        this.webUrl = webUrl;
    }

    public String getSshUrl() {
        return sshUrl;
    }

    public void setSshUrl(String sshUrl) {
        this.sshUrl = sshUrl;
    }

    public URL getHttpUrl() {
        return httpUrl;
    }

    public void setHttpUrl(URL httpUrl) {
        this.httpUrl = httpUrl;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public int getVisibility() {
        return visibility;
    }

    public void setVisibility(int visibility) {
        this.visibility = visibility;
    }

    public String getNamespacePath() {
        return namespacePath;
    }

    public void setNamespacePath(String namespacePath) {
        this.namespacePath = namespacePath;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }
}
