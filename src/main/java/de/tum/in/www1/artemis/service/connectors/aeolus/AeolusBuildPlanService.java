package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.service.connectors.bamboo.BambooInternalUrlService;

/**
 * Service for publishing custom build plans using Aeolus, currently supports Bamboo
 */
@Service
@Profile("aeolus")
public class AeolusBuildPlanService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AeolusBuildPlanService.class);

    private final Optional<BambooInternalUrlService> bambooInternalUrlService;

    private final RestTemplate restTemplate;

    @Value("${aeolus.url}")
    private URL aeolusUrl;

    @Value("${artemis.continuous-integration.token}")
    private String ciToken;

    @Value("${artemis.continuous-integration.user}")
    private String ciUsername;

    @Value("${artemis.continuous-integration.url}")
    private String ciUrl;

    /**
     * Constructor for the AeolusBuildPlanService
     *
     * @param restTemplate             the rest template to use
     * @param bambooInternalUrlService the internal URL service for Bamboo
     */
    public AeolusBuildPlanService(@Qualifier("aeolusRestTemplate") RestTemplate restTemplate, Optional<BambooInternalUrlService> bambooInternalUrlService) {
        this.restTemplate = restTemplate;
        this.bambooInternalUrlService = bambooInternalUrlService;
    }

    /**
     * Returns the internal URL of the CI server for Bamboo
     *
     * @return the internal URL of the CI server
     */
    private String getCiUrl() {
        return bambooInternalUrlService.map(internalUrlService -> internalUrlService.toInternalCiUrl(ciUrl)).orElse(null);
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
        if (url == null) {
            LOGGER.error("Could not publish build plan {} to Aeolus target {}, no CI URL configured", buildPlan, target);
            return null;
        }
        String requestUrl = aeolusUrl + "/publish/" + target;
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl);
        Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("url", url);
        jsonObject.put("username", ciUsername);
        jsonObject.put("token", ciToken);
        jsonObject.put("windfile", buildPlan);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(jsonObject, null);
        try {
            ResponseEntity<HashMap<String, String>> response = restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, entity, new ParameterizedTypeReference<>() {
            });
            if (response.getBody() != null) {
                return response.getBody().get("key");
            }
        }
        catch (HttpServerErrorException e) {
            LOGGER.error("Error while publishing build plan {} to Aeolus target {}", buildPlan, target, e);
        }
        return null;
    }
}
