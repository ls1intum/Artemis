package de.tum.cit.aet.artemis.lti.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI_AND_SCHEDULING;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.FullStartupEvent;

@Lazy
@Service
@Profile({ PROFILE_LTI_AND_SCHEDULING })
public class OAuth2JWKSInitialisationService {

    private final OAuth2JWKSService oAuth2JWKSService;

    private static final Logger log = LoggerFactory.getLogger(OAuth2JWKSInitialisationService.class);

    public OAuth2JWKSInitialisationService(OAuth2JWKSService oAuth2JWKSService) {
        this.oAuth2JWKSService = oAuth2JWKSService;
    }

    /**
     * Initializes the clientRegistration to JWK Map for OAuth2 ClientRegistrations.
     * This method is called once when the application starts, and it generates keys for all existing ClientRegistrations.
     */
    @EventListener(FullStartupEvent.class)
    public void init() {
        if (oAuth2JWKSService.getClientRegistrationIdToJwk().isEmpty()) {
            log.debug("Initializing JWKSet for OAuth2 ClientRegistrations");
            oAuth2JWKSService.generateOAuth2ClientKeys();
        }
    }
}
