package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.client.ApiClient;
import de.tum.cit.aet.artemis.hyperion.client.api.ReviewAndRefineApi;
import de.tum.cit.aet.artemis.hyperion.config.HyperionRestConfigurationProperties;

/**
 * Base service for Hyperion REST API interactions.
 *
 * Provides common functionality for all Hyperion services including
 * error handling, API client configuration, and consistent patterns.
 */
@Service
@Lazy
@Profile(PROFILE_HYPERION)
public abstract class AbstractHyperionRestService {

    private static final Logger log = LoggerFactory.getLogger(AbstractHyperionRestService.class);

    protected final HyperionRestConfigurationProperties hyperionProperties;

    protected final ApiClient apiClient;

    protected final ReviewAndRefineApi reviewAndRefineApi;

    protected AbstractHyperionRestService(@Qualifier("hyperionRestClient") RestClient restClient, HyperionRestConfigurationProperties hyperionProperties) {
        this.hyperionProperties = hyperionProperties;

        // Initialize the OpenAPI generated API client
        this.apiClient = new ApiClient(restClient);
        this.apiClient.setBasePath(hyperionProperties.getUrl());

        // Initialize API clients
        this.reviewAndRefineApi = new ReviewAndRefineApi(apiClient);
    }

    /**
     * Handles exceptions from Hyperion service calls and converts them to appropriate HTTP status exceptions.
     *
     * @param operation the operation that was being performed (for logging)
     * @param exception the exception that occurred
     * @throws NetworkingException the converted exception
     */
    protected void handleRestException(String operation, Exception exception) throws NetworkingException {
        String errorMessage = "Failed to execute " + operation + " via Hyperion REST API";
        log.error(errorMessage, exception);

        // Convert to appropriate networking exception
        if (exception instanceof org.springframework.web.client.ResourceAccessException) {
            throw new NetworkingException("Hyperion service is not available: " + exception.getMessage(), exception);
        }
        else if (exception instanceof RestClientResponseException clientException) {
            throw new NetworkingException("Hyperion service returned error (" + clientException.getStatusCode() + "): " + clientException.getMessage(), exception);
        }
        else {
            throw new NetworkingException(errorMessage + ": " + exception.getMessage(), exception);
        }
    }
}
