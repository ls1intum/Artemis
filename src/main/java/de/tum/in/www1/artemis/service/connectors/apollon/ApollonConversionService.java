package de.tum.in.www1.artemis.service.connectors.apollon;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.exception.NetworkingError;

@Service
@Profile("apollon")
public class ApollonConversionService {

    private final Logger log = LoggerFactory.getLogger(ApollonConversionService.class);

    @Value("${artemis.apollon.conversion-service-url}")
    private String apollonConversionUrl;

    private final ApollonConnector connector;

    public ApollonConversionService(@Qualifier("apollonRestTemplate") RestTemplate apollonRestTemplate) {
        connector = new ApollonConnector(log, apollonRestTemplate);
    }

    /**
     * Calls the remote Athene service to submit a Job for calculating automatic feedback
     * Falls back to naive splitting for less than 10 submissions
     * Note: See `TextSubmissionService:getTextSubmissionsByExerciseId` for selection of Submissions.
     * @param diagram the exercise the automatic assessments should be calculated for
     */
    public void convertDiagram(String diagram) {

        log.info("Calling Remote Service to convert for diagram.");

        try {
            final ApollonConnector.RequestDTO request = new ApollonConnector.RequestDTO(diagram);
            ApollonConnector.ResponseDTO response = connector.invoke(apollonConversionUrl + "/pdf", request);

        }
        catch (NetworkingError networkingError) {
            log.error("Error while calling Remote Service: {}", networkingError.getMessage());
        }
    }

}
