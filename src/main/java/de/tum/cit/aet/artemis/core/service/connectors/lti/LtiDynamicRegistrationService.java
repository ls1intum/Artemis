package de.tum.cit.aet.artemis.core.service.connectors.lti;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.security.OAuth2JWKSService;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.dto.Lti13ClientRegistration;
import de.tum.cit.aet.artemis.lti.dto.Lti13PlatformConfiguration;
import de.tum.cit.aet.artemis.lti.repository.LtiPlatformConfigurationRepository;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;

@Service
@Profile("lti")
public class LtiDynamicRegistrationService {

    @Value("${server.url}")
    private String artemisServerUrl;

    private static final Logger log = LoggerFactory.getLogger(LtiDynamicRegistrationService.class);

    private final LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository;

    private final OAuth2JWKSService oAuth2JWKSService;

    private final RestTemplate restTemplate;

    public LtiDynamicRegistrationService(LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository, OAuth2JWKSService oAuth2JWKSService, RestTemplate restTemplate) {
        this.oAuth2JWKSService = oAuth2JWKSService;
        this.restTemplate = restTemplate;
        this.ltiPlatformConfigurationRepository = ltiPlatformConfigurationRepository;
    }

    /**
     * Performs dynamic registration.
     *
     * @param openIdConfigurationUrl the url to get the configuration from
     * @param registrationToken      the token to be used to authenticate the POST request
     */
    public void performDynamicRegistration(String openIdConfigurationUrl, String registrationToken) {
        try {
            // Get platform's configuration
            Lti13PlatformConfiguration platformConfiguration = getLti13PlatformConfiguration(openIdConfigurationUrl);

            String clientRegistrationId = "artemis-" + UUID.randomUUID();

            if (platformConfiguration.authorizationEndpoint() == null || platformConfiguration.tokenEndpoint() == null || platformConfiguration.jwksUri() == null
                    || platformConfiguration.registrationEndpoint() == null) {
                throw new BadRequestAlertException("Invalid platform configuration", "LTI", "invalidPlatformConfiguration");
            }

            Lti13ClientRegistration clientRegistrationResponse = postClientRegistrationToPlatform(platformConfiguration.registrationEndpoint(), clientRegistrationId,
                    registrationToken);

            LtiPlatformConfiguration ltiPlatformConfiguration = updateLtiPlatformConfiguration(clientRegistrationId, platformConfiguration, clientRegistrationResponse);
            ltiPlatformConfigurationRepository.save(ltiPlatformConfiguration);

            oAuth2JWKSService.updateKey(clientRegistrationId);
        }
        catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("HTTP error during dynamic registration", ex);
            throw new IllegalStateException("Error during LTI dynamic registration: " + ex.getMessage(), ex);
        }
    }

    private Lti13PlatformConfiguration getLti13PlatformConfiguration(String openIdConfigurationUrl) {
        Lti13PlatformConfiguration platformConfiguration = null;
        try {
            ResponseEntity<Lti13PlatformConfiguration> responseEntity = restTemplate.getForEntity(openIdConfigurationUrl, Lti13PlatformConfiguration.class);
            log.info("Got LTI13 configuration from {}", openIdConfigurationUrl);
            platformConfiguration = responseEntity.getBody();
        }
        catch (HttpClientErrorException e) {
            log.error("Could not get configuration from {}", openIdConfigurationUrl);
        }

        if (platformConfiguration == null) {
            throw new BadRequestAlertException("Could not get configuration from external LMS", "LTI", "getConfigurationFailed");
        }
        return platformConfiguration;
    }

    private Lti13ClientRegistration postClientRegistrationToPlatform(String registrationEndpoint, String clientRegistrationId, String registrationToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (registrationToken != null) {
            headers.setBearerAuth(registrationToken);
        }

        Lti13ClientRegistration lti13ClientRegistration = new Lti13ClientRegistration(artemisServerUrl, clientRegistrationId);
        Lti13ClientRegistration registrationResponse = null;
        try {
            ResponseEntity<Lti13ClientRegistration> response = restTemplate.postForEntity(registrationEndpoint, new HttpEntity<>(lti13ClientRegistration, headers),
                    Lti13ClientRegistration.class);
            log.info("Registered {} as LTI1.3 tool at {}", artemisServerUrl, registrationEndpoint);
            registrationResponse = response.getBody();
        }
        catch (HttpClientErrorException e) {
            String message = "Could not register new client in external LMS at " + registrationEndpoint;
            log.error(message);
        }

        if (registrationResponse == null) {
            throw new BadRequestAlertException("Could not register configuration in external LMS", "LTI", "postConfigurationFailed");
        }
        return registrationResponse;
    }

    private LtiPlatformConfiguration updateLtiPlatformConfiguration(String registrationId, Lti13PlatformConfiguration platformConfiguration,
            Lti13ClientRegistration clientRegistrationResponse) {
        LtiPlatformConfiguration ltiPlatformConfiguration = new LtiPlatformConfiguration();
        ltiPlatformConfiguration.setRegistrationId(registrationId);
        ltiPlatformConfiguration.setClientId(clientRegistrationResponse.getClientId());
        ltiPlatformConfiguration.setAuthorizationUri(platformConfiguration.authorizationEndpoint());
        ltiPlatformConfiguration.setJwkSetUri(platformConfiguration.jwksUri());
        ltiPlatformConfiguration.setTokenUri(platformConfiguration.tokenEndpoint());
        ltiPlatformConfiguration.setOriginalUrl(platformConfiguration.issuer());
        return ltiPlatformConfiguration;
    }
}
