package de.tum.cit.aet.artemis.modeling.service.apollon;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.modeling.service.apollon.dto.ApollonModelDTO;

@Service
@Profile("apollon")
public class ApollonConversionService {

    private static final Logger log = LoggerFactory.getLogger(ApollonConversionService.class);

    @Value("${artemis.apollon.conversion-service-url}")
    private String apollonConversionUrl;

    private RestTemplate restTemplate;

    public ApollonConversionService(RestTemplate apollonRestTemplate) {
        setRestTemplate(apollonRestTemplate);
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Calls the remote Apollon conversion service to convert given model to pdf
     *
     * @param model the model to convert to pdf
     * @return an input stream that is coming from apollon conversion server
     */
    public InputStream convertModel(String model) {

        log.info("Calling Remote Service to convert for model.");
        try {
            var apollonModel = new ApollonModelDTO(model);
            var response = restTemplate.postForEntity(apollonConversionUrl + "/pdf", apollonModel, Resource.class);
            if (response.getBody() != null) {
                return response.getBody().getInputStream();
            }
        }
        catch (HttpClientErrorException ex) {
            log.error("Error while calling Remote Service: {}", ex.getMessage());
        }
        catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
        return null;

    }

}
