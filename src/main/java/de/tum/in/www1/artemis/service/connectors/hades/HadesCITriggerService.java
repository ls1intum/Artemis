package de.tum.in.www1.artemis.service.connectors.hades;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;

@Service
@Profile("hades")
public class HadesCITriggerService implements ContinuousIntegrationTriggerService {

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    @Value("${artemis.hades.url}")
    private String hadesServerUrl;

    public HadesCITriggerService(RestTemplate restTemplate, MappingJackson2HttpMessageConverter springMvcJacksonConverter) {
        this.restTemplate = restTemplate;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws ContinuousIntegrationException {
    }

}
