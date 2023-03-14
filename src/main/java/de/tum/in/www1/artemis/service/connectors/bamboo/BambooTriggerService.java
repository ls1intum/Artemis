package de.tum.in.www1.artemis.service.connectors.bamboo;

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.BambooException;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;

@Service
@Profile("bamboo")
public class BambooTriggerService implements ContinuousIntegrationTriggerService {

    private final Logger log = LoggerFactory.getLogger(BambooTriggerService.class);

    @Value("${artemis.continuous-integration.url}")
    protected URL serverUrl;

    private final RestTemplate restTemplate;

    public BambooTriggerService(@Qualifier("bambooRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Triggers a build for the build plan in the given participation.
     *
     * @param participation the participation with the id of the build plan that should be triggered.
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws BambooException {
        var buildPlan = participation.getBuildPlanId();
        try {
            restTemplate.exchange(serverUrl + "/rest/api/latest/queue/" + buildPlan, HttpMethod.POST, null, Void.class);
        }
        catch (RestClientException e) {
            log.error("HttpError while triggering build plan {} with error: {}", buildPlan, e.getMessage());
            throw new BambooException("Communication failed when trying to trigger the Bamboo build plan " + buildPlan + " with the error: " + e.getMessage());
        }
    }
}
