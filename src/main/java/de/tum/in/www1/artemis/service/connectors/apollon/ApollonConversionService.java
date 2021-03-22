package de.tum.in.www1.artemis.service.connectors.apollon;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.web.rest.dto.ApollonConversionDTO;

@Service
// @Profile("apollon")
public class ApollonConversionService {

    private final Logger log = LoggerFactory.getLogger(ApollonConversionService.class);

    // @Value("${artemis.apollon.conversion-service-url}")
    private String apollonConversionUrl = "http://localhost:8001/api";

    private final ApollonConnector connector;

    public ApollonConversionService(RestTemplate apollonRestTemplate) {
        connector = new ApollonConnector(log, apollonRestTemplate);
    }

    /**
     * Calls the remote Athene service to submit a Job for calculating automatic feedback
     * Falls back to naive splitting for less than 10 submissions
     * Note: See `TextSubmissionService:getTextSubmissionsByExerciseId` for selection of Submissions.
     * @param model the exercise the automatic assessments should be calculated for
     */
    public InputStream convertDiagram(String model) {

        log.info("Calling Remote Service to convert for diagram.");
        System.out.println(model);
        ApollonConversionDTO dto = new ApollonConversionDTO();
        dto.setDiagram(model);
        try {
            final ApollonConnector.RequestDTO request = new ApollonConnector.RequestDTO(model);
            InputStream response = connector.invoke(apollonConversionUrl + "/pdf", request);
            return response;
        }
        catch (NetworkingError networkingError) {
            log.error("Error while calling Remote Service: {}", networkingError.getMessage());
        }
        return null;

    }

}
