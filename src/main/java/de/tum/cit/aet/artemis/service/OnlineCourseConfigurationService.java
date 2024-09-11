package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.LOGIN_REGEX;
import static de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration.ENTITY_NAME;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.lti.config.CustomLti13Configurer;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.lti.repository.LtiPlatformConfigurationRepository;

/**
 * Service Implementation for OnlineCourseConfiguration.
 */
@Service
@Profile("lti")
public class OnlineCourseConfigurationService implements ClientRegistrationRepository {

    private static final Logger log = LoggerFactory.getLogger(OnlineCourseConfigurationService.class);

    private final LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository;

    @Value("${server.url}")
    private String artemisServerUrl;

    public OnlineCourseConfigurationService(LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository) {
        this.ltiPlatformConfigurationRepository = ltiPlatformConfigurationRepository;
    }

    public List<ClientRegistration> getAllClientRegistrations() {
        // TODO: we should avoid findAll() and instead try to retrieve the correct object directly from the database, potentially in a batch
        return ltiPlatformConfigurationRepository.findAll().stream().map(this::getClientRegistration).filter(Objects::nonNull).toList();
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        Optional<LtiPlatformConfiguration> ltiPlatformConfiguration = ltiPlatformConfigurationRepository.findByRegistrationId(registrationId);
        return ltiPlatformConfiguration.map(this::getClientRegistration).orElse(null);
    }

    /**
     * Creates an initial configuration for online courses with default and random values
     *
     * @param course the online course we create a configuration for
     */
    public void createOnlineCourseConfiguration(Course course) {
        OnlineCourseConfiguration ocConfiguration = new OnlineCourseConfiguration();
        ocConfiguration.setCourse(course);
        ocConfiguration.setUserPrefix(course.getShortName());
        course.setOnlineCourseConfiguration(ocConfiguration);
    }

    /**
     * Validates the online course configuration
     *
     * @param ocConfiguration the online course configuration being validated
     */
    public void validateOnlineCourseConfiguration(OnlineCourseConfiguration ocConfiguration) {
        if (StringUtils.isBlank(ocConfiguration.getUserPrefix()) || !ocConfiguration.getUserPrefix().matches(LOGIN_REGEX)) {
            throw new BadRequestAlertException("Invalid user prefix, must match login regex defined in Constants.java", ENTITY_NAME, "invalidUserPrefix");
        }

        if (ocConfiguration.getLtiPlatformConfiguration() != null) {
            Optional<LtiPlatformConfiguration> existingLtiPlatformConfiguration = ltiPlatformConfigurationRepository
                    .findByRegistrationId(ocConfiguration.getLtiPlatformConfiguration().getRegistrationId());
            if (existingLtiPlatformConfiguration.isEmpty()
                    || !Objects.equals(existingLtiPlatformConfiguration.get().getId(), ocConfiguration.getLtiPlatformConfiguration().getId())) {
                throw new BadRequestAlertException("No platform registration found", ENTITY_NAME, "invalidRegistrationId");
            }
        }
    }

    /**
     * Converts the ltiPlatformConfiguration to a ClientRegistration if the necessary fields are filled
     *
     * @param ltiPlatformConfiguration the lti platform configuration
     * @return the clientRegistration from the converted lti platform configuration
     */
    public ClientRegistration getClientRegistration(LtiPlatformConfiguration ltiPlatformConfiguration) {
        if (ltiPlatformConfiguration == null) {
            return null;
        }
        try {
            return ClientRegistration.withRegistrationId(ltiPlatformConfiguration.getRegistrationId()) // formatting
                    .clientId(ltiPlatformConfiguration.getClientId()) //
                    .authorizationUri(ltiPlatformConfiguration.getAuthorizationUri()) //
                    .jwkSetUri(ltiPlatformConfiguration.getJwkSetUri()) //
                    .tokenUri(ltiPlatformConfiguration.getTokenUri()) //
                    .redirectUri(artemisServerUrl + "/" + CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH) //
                    .scope("openid") //
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE) //
                    .build();
        }
        catch (IllegalArgumentException e) {
            // Log a warning for rare scenarios i.e. ClientId is empty. This can occur when online courses lack an external LMS connection or use LTI v1.0.
            log.warn("Could not build Client Registration from ltiPlatformConfiguration. Reason: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Associates an online course configuration with an LTI platform configuration.
     * If the provided online course configuration has a linked LTI platform configuration,
     * it is added to the platform's list of online course configurations.
     *
     * @param onlineCourseConfiguration The online course configuration to be associated.
     */
    public void addOnlineCourseConfigurationToLtiConfigurations(OnlineCourseConfiguration onlineCourseConfiguration) {
        if (onlineCourseConfiguration.getLtiPlatformConfiguration() != null) {
            Long platformId = onlineCourseConfiguration.getLtiPlatformConfiguration().getId();
            LtiPlatformConfiguration platformConfiguration = ltiPlatformConfigurationRepository.findLtiPlatformConfigurationWithEagerLoadedCoursesByIdElseThrow(platformId);

            var setOfOnlineCourses = platformConfiguration.getOnlineCourseConfigurations();
            setOfOnlineCourses.add(onlineCourseConfiguration);
            onlineCourseConfiguration.setLtiPlatformConfiguration(platformConfiguration);
        }
    }
}
