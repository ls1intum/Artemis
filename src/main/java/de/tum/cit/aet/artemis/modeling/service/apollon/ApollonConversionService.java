package de.tum.cit.aet.artemis.modeling.service.apollon;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import de.tum.cit.aet.artemis.modeling.config.ApollonEnabled;
import de.tum.cit.aet.artemis.modeling.dto.ApollonModelDTO;

@Lazy
@Service
@Conditional(ApollonEnabled.class)
public class ApollonConversionService {

    private static final Logger log = LoggerFactory.getLogger(ApollonConversionService.class);

    @Value("${artemis.apollon.conversion-service-url}")
    private String apollonConversionUrl;

    private final RestClient restClient;

    public ApollonConversionService(@Qualifier("apollonRestClient") RestClient apollonRestClient) {
        this.restClient = apollonRestClient;
    }

    /**
     * Calls the remote Apollon conversion service to convert given model to pdf
     *
     * @param model the model to convert to pdf
     * @return an input stream containing the PDF data
     * @throws IOException if the conversion fails or returns an empty response
     */
    public InputStream convertModel(String model) throws IOException {
        log.debug("Calling Remote Service to convert for model.");
        try {
            var apollonModel = new ApollonModelDTO(model);

            var response = restClient.post().uri(apollonConversionUrl + "/api/converter/pdf").contentType(MediaType.APPLICATION_JSON).body(apollonModel).retrieve()
                    .toEntity(Resource.class);

            if (response.getBody() != null) {
                return response.getBody().getInputStream();
            }

            throw new IOException("Apollon conversion service returned an empty response body");
        }
        catch (RestClientException ex) {
            throw new IOException("Error while calling Apollon conversion service: " + ex.getMessage(), ex);
        }
    }
}
