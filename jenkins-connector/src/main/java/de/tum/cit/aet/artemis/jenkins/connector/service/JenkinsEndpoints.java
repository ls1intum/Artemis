package de.tum.cit.aet.artemis.jenkins.connector.service;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.jenkins.connector.util.UrlUtils;

public enum JenkinsEndpoints {

    // @formatter:off
    // Build plan endpoints
    NEW_PLAN("job", "<projectKey>", "createItem"),
    NEW_FOLDER("createItem"),
    DELETE_FOLDER("job", "<projectKey>", "doDelete"),
    DELETE_JOB("job", "<projectKey>", "job", "<planName>", "doDelete"),
    PLAN_CONFIG("job", "<projectKey>", "job", "<planKey>", "config.xml"),
    FOLDER_CONFIG("job", "<projectKey>", "config.xml"),
    TRIGGER_BUILD("job", "<projectKey>", "job", "<planKey>", "build"),
    ENABLE("job", "<projectKey>", "job", "<planKey>", "enable"),
    LAST_BUILD("job", "<projectKey>", "job", "<planKey>", "lastBuild", "api", "json"),
    GET_FOLDER_JOB("job", "<projectKey>", "api", "json"),
    GET_JOB("job", "<projectKey>", "job", "<planKey>", "api", "json"),

    // Health
    HEALTH("login"),

    // User management endpoints
    GET_USER("user", "<username>", "api", "json"),
    DELETE_USER("user", "<username>", "doDelete"),
    CREATE_USER("securityRealm", "createAccountByAdmin");
    // @formatter:on

    private final List<String> pathSegments;

    JenkinsEndpoints(String... pathSegments) {
        this.pathSegments = Arrays.asList(pathSegments);
    }

    public UriComponentsBuilder buildEndpoint(URI baseUri, Object... args) {
        return UrlUtils.buildEndpoint(baseUri, pathSegments, args);
    }
}