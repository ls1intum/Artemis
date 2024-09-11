package de.tum.cit.aet.artemis.service.connectors.jenkins;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.service.util.UrlUtils;

public enum JenkinsEndpoints {

    NEW_PLAN("job", "<projectKey>", "createItem"), NEW_FOLDER("createItem"), DELETE_FOLDER("job", "<projectKey>", "doDelete"),
    DELETE_JOB("job", "<projectKey>", "job", "<planName>", "doDelete"), PLAN_CONFIG("job", "<projectKey>", "job", "<planKey>", "config.xml"),
    TRIGGER_BUILD("job", "<projectKey>", "job", "<planKey>", "build"), ENABLE("job", "<projectKey>", "job", "<planKey>", "enable"),
    TEST_RESULTS("job", "<projectKey>", "job", "<planKey>", "lastBuild", "testResults", "api", "json"),
    LAST_BUILD("job", "<projectKey>", "job", "<planKey>", "lastBuild", "api", "json");

    private final List<String> pathSegments;

    JenkinsEndpoints(String... pathSegments) {
        this.pathSegments = Arrays.asList(pathSegments);
    }

    public UriComponentsBuilder buildEndpoint(String baseUrl, Object... args) {
        return UrlUtils.buildEndpoint(baseUrl, pathSegments, args);
    }
}
