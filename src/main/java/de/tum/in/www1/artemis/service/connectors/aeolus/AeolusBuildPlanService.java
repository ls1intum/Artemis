package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.net.URL;
import java.util.*;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.service.connectors.bamboo.BambooInternalUrlService;
import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsInternalUrlService;

/**
 * Service for publishing custom build plans using Aeolus, supports Bamboo and Jenkins
 */
@Service
@Profile("aeolus")
public class AeolusBuildPlanService {

    @Value("${aeolus.url}")
    private URL aeolusUrl;

    @Value("${artemis.continuous-integration.token}")
    private String ciToken;

    @Value("${artemis.continuous-integration.user}")
    private String ciUsername;

    @Value("${artemis.continuous-integration.url}")
    private String ciUrl;

    private final Optional<JenkinsInternalUrlService> jenkinsInternalUrlService;

    private final Optional<BambooInternalUrlService> bambooInternalUrlService;

    private final RestTemplate restTemplate;

    public AeolusBuildPlanService(@Qualifier("aeolusRestTemplate") RestTemplate restTemplate, Optional<BambooInternalUrlService> bambooInternalUrlService,
            Optional<JenkinsInternalUrlService> jenkinsInternalUrlService) {
        this.restTemplate = restTemplate;
        this.jenkinsInternalUrlService = jenkinsInternalUrlService;
        this.bambooInternalUrlService = bambooInternalUrlService;
    }

    /**
     * Returns the internal URL of the CI server for either Jenkins or Bamboo
     * depending on which one is configured
     *
     * @return the internal URL of the CI server
     */
    private String getCiUrl() {
        if (jenkinsInternalUrlService.isPresent()) {
            return jenkinsInternalUrlService.get().toInternalCiUrl(ciUrl);
        }
        else if (bambooInternalUrlService.isPresent()) {
            return bambooInternalUrlService.get().toInternalCiUrl(ciUrl);
        }
        return null;
    }

    /**
     * Publishes a build plan using Aeolus
     *
     * @param buildPlan the build plan to publish
     * @param target    the target to publish to, either bamboo or jenkins
     * @return the key of the published build plan
     */
    public String publishBuildPlan(String buildPlan, String target) {
        String url = getCiUrl();
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        String requestUrl = aeolusUrl + "/publish/" + target;
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);
        Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("url", url);
        jsonObject.put("username", ciUsername);
        jsonObject.put("token", ciToken);
        jsonObject.put("windfile", buildPlan);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(jsonObject, null);
        var response = restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, entity, Map.class);
        return Objects.requireNonNull(response.getBody()).get("key").toString();
    }
}
