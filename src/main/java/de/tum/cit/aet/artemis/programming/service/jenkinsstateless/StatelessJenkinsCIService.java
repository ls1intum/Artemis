package de.tum.cit.aet.artemis.programming.service.jenkinsstateless;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.service.ci.StatelessCIService;

/**
 * Implementation of ContinuousIntegrationConnectorService that communicates
 * with an external CI connector microservice.
 * This service handles all CI operations by delegating to the microservice via
 * REST API calls.
 */
@Service
@Profile("statless-jenkins")
public class StatelessJenkinsCIService implements StatelessCIService {

    private static final Logger log = LoggerFactory.getLogger(StatelessJenkinsCIService.class);

    private final RestTemplate restTemplate;

    @Value("${artemis.external-ci.url:http://localhost:8081}")
    private String connectorBaseUrl;

    public StatelessJenkinsCIService(@Qualifier("externalCIRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public ConnectorHealth health() {

        // TODO Implement actual health check logic for the external CI connector
        return new ConnectorHealth(true, Map.of("status", "up"), null);
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBuildStatus'");
    }

    @Override
    public UUID build(BuildTriggerRequestDTO buildTriggerRequestDTO) throws ContinuousIntegrationException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'build'");
    }
}
