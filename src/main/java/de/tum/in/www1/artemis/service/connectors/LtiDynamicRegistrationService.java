package de.tum.in.www1.artemis.service.connectors;

import java.util.UUID;

import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.domain.lti.Lti13ClientRegistration;
import de.tum.in.www1.artemis.domain.lti.Lti13PlatformConfiguration;
import de.tum.in.www1.artemis.repository.OnlineCourseConfigurationRepository;
import de.tum.in.www1.artemis.security.OAuth2JWKSService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class LtiDynamicRegistrationService {

    @Value("${server.url}")
    private String artemisServerUrl;

    private final Logger log = LoggerFactory.getLogger(LtiDynamicRegistrationService.class);

    private final OnlineCourseConfigurationRepository onlineCourseConfigurationRepository;

    private final OAuth2JWKSService oAuth2JWKSService;

    private final RestTemplate restTemplate;

    public LtiDynamicRegistrationService(OnlineCourseConfigurationRepository onlineCourseConfigurationRepository, OAuth2JWKSService oAuth2JWKSService, RestTemplate restTemplate) {

        this.onlineCourseConfigurationRepository = onlineCourseConfigurationRepository;
        this.oAuth2JWKSService = oAuth2JWKSService;
        this.restTemplate = restTemplate;
    }

    /**
     * Performs dynamic registration.
     *
     * @param course the online course to register as an LTI1.3 tool
     * @param openIdConfigurationUrl the url to get the configuration from
     * @param registrationToken the token to be used to authenticate the POST request
     */
    public void performDynamicRegistration(Course course, String openIdConfigurationUrl, String registrationToken) {
        if (!course.isOnlineCourse()) {
            throw new BadRequestAlertException("LTI is not configured for this course", "LTI", "ltiNotConfigured");
        }

        // Get platform's configuration
        Lti13PlatformConfiguration platformConfiguration = getLti13PlatformConfiguration(openIdConfigurationUrl);

        String clientRegistrationId = course.getShortName() + UUID.randomUUID();

        if (platformConfiguration.getAuthorizationEndpoint() == null || platformConfiguration.getTokenEndpoint() == null || platformConfiguration.getJwksUri() == null
                || platformConfiguration.getRegistrationEndpoint() == null) {
            throw new BadRequestAlertException("Invalid platform configuration", "LTI", "invalidPlatformConfiguration");
        }

        Lti13ClientRegistration clientRegistrationResponse = postClientRegistrationToPlatform(platformConfiguration.getRegistrationEndpoint(), course, clientRegistrationId,
                registrationToken);

        OnlineCourseConfiguration onlineCourseConfiguration = createOrUpdateOnlineCourseConfiguration(course, clientRegistrationId, platformConfiguration,
                clientRegistrationResponse);
        onlineCourseConfigurationRepository.save(onlineCourseConfiguration);

        oAuth2JWKSService.updateKey(clientRegistrationId);
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

    private Lti13ClientRegistration postClientRegistrationToPlatform(String registrationEndpoint, Course course, String clientRegistrationId, String registrationToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (registrationToken != null) {
            headers.setBearerAuth(registrationToken);
        }

        Lti13ClientRegistration lti13ClientRegistration = new Lti13ClientRegistration(artemisServerUrl, course, clientRegistrationId);
        Lti13ClientRegistration registrationResponse = null;
        try {
            ResponseEntity<Lti13ClientRegistration> response = restTemplate.postForEntity(registrationEndpoint, new HttpEntity<>(lti13ClientRegistration, headers),
                    Lti13ClientRegistration.class);
            log.info("Registered course {} as LTI1.3 tool at {}", course.getTitle(), registrationEndpoint);
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

    private OnlineCourseConfiguration createOrUpdateOnlineCourseConfiguration(Course course, String registrationId, Lti13PlatformConfiguration platformConfiguration,
            Lti13ClientRegistration clientRegistrationResponse) {
        OnlineCourseConfiguration ocConfiguration = course.getOnlineCourseConfiguration();
        if (ocConfiguration == null) {
            ocConfiguration = new OnlineCourseConfiguration();
            ocConfiguration.setCourse(course);
            ocConfiguration.setLtiKey(RandomStringUtils.random(12, true, true));
            ocConfiguration.setLtiSecret(RandomStringUtils.random(12, true, true));
            ocConfiguration.setUserPrefix(course.getShortName() + "_");
        }

        ocConfiguration.setRegistrationId(registrationId);
        ocConfiguration.setClientId(clientRegistrationResponse.getClientId());
        ocConfiguration.setAuthorizationUri(platformConfiguration.getAuthorizationEndpoint());
        ocConfiguration.setJwkSetUri(platformConfiguration.getJwksUri());
        ocConfiguration.setTokenUri(platformConfiguration.getTokenEndpoint());
        return ocConfiguration;
    }
}
