package de.tum.cit.aet.artemis.lti.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lti.config.LtiEnabled;

@Lazy
@Service
@Conditional(LtiEnabled.class)
@Profile(PROFILE_SCHEDULING)
public class OAuth2JWKSInitialisationService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2JWKSInitialisationService.class);

    private final OAuth2JWKSService oAuth2JWKSService;

    private final OnlineCourseConfigurationService onlineCourseConfigurationService;

    public OAuth2JWKSInitialisationService(OAuth2JWKSService oAuth2JWKSService, OnlineCourseConfigurationService onlineCourseConfigurationService) {
        this.oAuth2JWKSService = oAuth2JWKSService;
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
    }

    /**
     * Initializes the clientRegistration to JWK Map for OAuth2 ClientRegistrations.
     * This method is called once when the application starts, and it generates keys for all existing ClientRegistrations.
     */
    @PostConstruct
    public void init() {
        if (oAuth2JWKSService.getClientRegistrationIdToJwk().isEmpty()) {
            log.info("Initializing JWKSet for OAuth2 ClientRegistrations");
            generateOAuth2ClientKeys();
        }
    }

    /**
     * Generates a new JWK for each OAuth2 ClientRegistration, if it is not present in the Hazelcast map and stores it.
     * This method is called once during initialization to ensure all existing ClientRegistrations have a key.
     */
    private void generateOAuth2ClientKeys() {
        onlineCourseConfigurationService.getAllClientRegistrations()
                .forEach(cr -> oAuth2JWKSService.getClientRegistrationIdToJwk().computeIfAbsent(cr.getRegistrationId(), id -> oAuth2JWKSService.generateKey(cr)));
    }
}
