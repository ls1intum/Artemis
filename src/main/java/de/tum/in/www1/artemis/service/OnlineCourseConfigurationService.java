package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.LOGIN_REGEX;
import static de.tum.in.www1.artemis.domain.OnlineCourseConfiguration.ENTITY_NAME;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.lti.CustomLti13Configurer;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.repository.OnlineCourseConfigurationRepository;
import de.tum.in.www1.artemis.security.OAuth2JWKSService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * Service Implementation for OnlineCourseConfiguration.
 */
@Service
public class OnlineCourseConfigurationService implements ClientRegistrationRepository {

    private final Logger log = LoggerFactory.getLogger(OAuth2JWKSService.class);

    private final OnlineCourseConfigurationRepository onlineCourseConfigurationRepository;

    @Value("${server.url}")
    private String artemisServerUrl;

    public OnlineCourseConfigurationService(OnlineCourseConfigurationRepository onlineCourseConfigurationRepository) {
        this.onlineCourseConfigurationRepository = onlineCourseConfigurationRepository;
    }

    public List<ClientRegistration> getAllClientRegistrations() {
        return onlineCourseConfigurationRepository.findAll().stream().map(this::getClientRegistration).filter(Objects::nonNull).toList();
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        Optional<OnlineCourseConfiguration> onlineCourseConfiguration = onlineCourseConfigurationRepository.findByRegistrationId(registrationId);
        return onlineCourseConfiguration.map(this::getClientRegistration).orElse(null);
    }

    /**
     * Validates the online course configuration if the course is an online course
     * @param course the course for which the online course configuration should be validated
     * @throws BadRequestAlertException 400 (Bad Request) if the online course configuration is invalid
     */
    public void validateOnlineCourseConfiguration(Course course) {
        if (!course.isOnlineCourse()) {
            return;
        }
        OnlineCourseConfiguration ocConfiguration = course.getOnlineCourseConfiguration();
        if (ocConfiguration == null) {
            throw new BadRequestAlertException("Configuration must exist for online courses", ENTITY_NAME, "onlineCourseConfigurationMissing");
        }

        if (StringUtils.isBlank(ocConfiguration.getLtiKey()) || StringUtils.isBlank(ocConfiguration.getLtiSecret())) {
            throw new BadRequestAlertException("Invalid online course configuration", ENTITY_NAME, "invalidOnlineCourseConfiguration");
        }
        if (StringUtils.isBlank(ocConfiguration.getUserPrefix()) || !ocConfiguration.getUserPrefix().matches(LOGIN_REGEX)) {
            throw new BadRequestAlertException("Invalid user prefix, must match login regex defined in Constants.java", ENTITY_NAME, "invalidUserPrefix");
        }

        Optional<OnlineCourseConfiguration> existingOnlineCourseConfiguration = onlineCourseConfigurationRepository.findByRegistrationId(ocConfiguration.getRegistrationId());
        if (existingOnlineCourseConfiguration.isPresent() && !Objects.equals(existingOnlineCourseConfiguration.get().getId(), ocConfiguration.getId())) {
            throw new BadRequestAlertException("Registration ID must be unique", ENTITY_NAME, "invalidRegistrationId");
        }
    }

    /**
     * Converts the onlineCourseConfiguration to a ClientRegistration if the necessary fields are filled
     *
     * @param onlineCourseConfiguration the online course configuration
     * @return the clientRegistration from the converted online course configuration
     */
    public ClientRegistration getClientRegistration(OnlineCourseConfiguration onlineCourseConfiguration) {
        if (onlineCourseConfiguration == null) {
            return null;
        }
        try {
            return ClientRegistration.withRegistrationId(onlineCourseConfiguration.getRegistrationId()) // formatting
                    .clientId(onlineCourseConfiguration.getClientId()) //
                    .authorizationUri(onlineCourseConfiguration.getAuthorizationUri()) //
                    .jwkSetUri(onlineCourseConfiguration.getJwkSetUri()) //
                    .tokenUri(onlineCourseConfiguration.getTokenUri()) //
                    .redirectUri(artemisServerUrl + CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH) //
                    .scope("openid") //
                    .authorizationGrantType(AuthorizationGrantType.IMPLICIT) //
                    .build();
        }
        catch (IllegalArgumentException e) {
            log.error("Could not build Client Registration from onlineCourseConfiguration");
            return null;
        }
    }
}
