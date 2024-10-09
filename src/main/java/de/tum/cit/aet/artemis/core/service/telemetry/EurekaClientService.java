package de.tum.cit.aet.artemis.core.service.telemetry;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Profile(PROFILE_SCHEDULING)
@Service
public class EurekaClientService {

    private static final Logger log = LoggerFactory.getLogger(EurekaClientService.class);

    @Value("${eureka.client.service-url.defaultZone}")
    private String eurekaServiceUrl;

    private final RestTemplate restTemplate;

    public EurekaClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Retrieves the number of Artemis application instances registered with the Eureka server.
     * <p>
     * This method makes an HTTP GET request to the Eureka server's `/api/eureka/applications` endpoint to
     * retrieve a list of all registered applications. It then filters out the Artemis application
     * and returns the number of instances associated with it. If any error occurs during the request
     * (e.g., network issues, parsing issues, invalid URI), the method returns 1 as the default value.
     *
     * @return the number of Artemis application instances, or 1 if an error occurs.
     */
    public long getNumberOfReplicas() {
        try {
            var eurekaURI = new URI(eurekaServiceUrl);
            HttpHeaders headers = createHeaders(eurekaURI.getUserInfo());
            HttpEntity<String> request = new HttpEntity<>(headers);
            var requestUrl = eurekaURI.getScheme() + "://" + eurekaURI.getAuthority() + "/api/eureka/applications";

            ResponseEntity<String> response = restTemplate.exchange(requestUrl, HttpMethod.GET, request, String.class);

            for (JsonNode application : new ObjectMapper().readTree(response.getBody()).get("applications")) {
                if (application.get("name").asText().equals("ARTEMIS")) {
                    return application.get("instances").size();
                }
            }
        }
        catch (Exception e) {
            log.warn("Error while trying to retrieve number of replicas.");
        }

        return 1;
    }

    /**
     * Creates HTTP headers with Basic Authentication and JSON content type.
     *
     * @param auth the user credentials in the format "username:password" to be encoded and included in the Authorization header.
     * @return HttpHeaders with Basic Authentication and JSON content types.
     */
    private HttpHeaders createHeaders(String auth) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.US_ASCII));
        String authHeader = "Basic " + new String(encodedAuth);

        headers.set("Authorization", authHeader);
        return headers;
    }
}
