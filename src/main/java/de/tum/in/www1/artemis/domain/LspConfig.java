package de.tum.in.www1.artemis.domain;

import java.net.URL;

import org.springframework.context.annotation.Profile;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A helper object used to represent the configs passed to the client
 * for the Language Server Protocol connections
 */
@Profile("lsp")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LspConfig {

    private URL serverUrl;

    private String repoPath;

    private String containerId;

    public URL getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(URL serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getRepoPath() {
        return repoPath;
    }

    public void setRepoPath(String repoPath) {
        this.repoPath = repoPath;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }
}
