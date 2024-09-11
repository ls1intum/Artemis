package de.tum.cit.aet.artemis.core.web.admin;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.OAuth2JWKSService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.repository.LtiPlatformConfigurationRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.connectors.lti.LtiDynamicRegistrationService;
import de.tum.cit.aet.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing LTI platform configurations.
 * Handles administrative actions for LTI platforms, including configuration, deletion, and dynamic registration.
 */
@RestController
@RequestMapping("api/admin/")
@Profile("lti")
public class AdminLtiConfigurationResource {

    private static final String ENTITY_NAME = "lti-platform";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final Logger log = LoggerFactory.getLogger(AdminLtiConfigurationResource.class);

    private final LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository;

    private final LtiDynamicRegistrationService ltiDynamicRegistrationService;

    private final AuthorizationCheckService authCheckService;

    private final OAuth2JWKSService oAuth2JWKSService;

    /**
     * Constructor to initialize the controller with necessary services.
     *
     * @param ltiPlatformConfigurationRepository Repository for LTI platform configurations.
     * @param ltiDynamicRegistrationService      Service for LTI dynamic registration.
     * @param authCheckService                   Service for authorization checks.
     */
    public AdminLtiConfigurationResource(LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository, LtiDynamicRegistrationService ltiDynamicRegistrationService,
            AuthorizationCheckService authCheckService, OAuth2JWKSService oAuth2JWKSService) {
        this.ltiPlatformConfigurationRepository = ltiPlatformConfigurationRepository;
        this.ltiDynamicRegistrationService = ltiDynamicRegistrationService;
        this.authCheckService = authCheckService;
        this.oAuth2JWKSService = oAuth2JWKSService;
    }

    /**
     * Fetches LTI platform configuration based on the given platform ID.
     * Returns {@code ResponseEntity} with status OK if found, or NOT_FOUND if no configuration exists.
     *
     * @param platformId The ID of the LTI platform to retrieve configuration for.
     * @return a {@code ResponseEntity} with an {@code Optional<LtiPlatformConfiguration>} and HTTP status.
     */
    @GetMapping("lti-platform/{platformId}")
    @EnforceAdmin
    public ResponseEntity<LtiPlatformConfiguration> getLtiPlatformConfiguration(@PathVariable("platformId") String platformId) {
        log.debug("REST request to configured lti platform");
        LtiPlatformConfiguration platform = ltiPlatformConfigurationRepository.findByIdElseThrow(Long.parseLong(platformId));
        return new ResponseEntity<>(platform, HttpStatus.OK);
    }

    /**
     * Deletes the LTI platform configuration for the given platform ID.
     *
     * @param platformId the ID of the platform configuration to delete.
     * @return a {@code ResponseEntity<Void>} with status {@code 200 (OK)} and a header indicating the deletion.
     */
    @DeleteMapping("lti-platform/{platformId}")
    @EnforceAdmin
    public ResponseEntity<Void> deleteLtiPlatformConfiguration(@PathVariable("platformId") String platformId) {
        log.debug("REST request to configured lti platform");
        LtiPlatformConfiguration platform = ltiPlatformConfigurationRepository.findByIdElseThrow(Long.parseLong(platformId));
        ltiPlatformConfigurationRepository.delete(platform);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, platformId)).build();
    }

    /**
     * Updates an existing LTI platform configuration.
     *
     * @param platform the updated LTI platform configuration to be saved.
     * @return a {@link ResponseEntity} with status 200 (OK) if the update was successful,
     *         or with status 400 (Bad Request) if the provided platform configuration is invalid (e.g., missing ID)
     */
    @PutMapping("lti-platform")
    @EnforceAdmin
    public ResponseEntity<Void> updateLtiPlatformConfiguration(@RequestBody LtiPlatformConfiguration platform) {
        log.debug("REST request to update configured lti platform");

        if (platform.getId() == null) {
            return ResponseEntity.badRequest().build();
        }

        ltiPlatformConfigurationRepository.save(platform);
        return ResponseEntity.ok().build();
    }

    /**
     * Updates an existing LTI platform configuration.
     *
     * @param platform the updated LTI platform configuration to be saved.
     * @return a {@link ResponseEntity} with status 200 (OK) if the update was successful,
     *         or with status 400 (Bad Request) if the provided platform configuration is invalid (e.g., missing ID)
     */
    @PostMapping("lti-platform")
    @EnforceAdmin
    public ResponseEntity<Void> addLtiPlatformConfiguration(@RequestBody LtiPlatformConfiguration platform) {
        log.debug("REST request to add new lti platform");

        String clientRegistrationId = "artemis-" + UUID.randomUUID();
        platform.setRegistrationId(clientRegistrationId);

        ltiPlatformConfigurationRepository.save(platform);
        oAuth2JWKSService.updateKey(clientRegistrationId);

        return ResponseEntity.ok().build();
    }

    /**
     * Handles the dynamic registration process for LTI 1.3 platforms. It uses the provided OpenID
     * configuration and an optional registration token.
     *
     * @param openIdConfiguration The OpenID Connect discovery configuration URL.
     * @param registrationToken   Optional token for the registration process.
     * @return a {@link ResponseEntity} with status 200 (OK) if the dynamic registration process was successful.
     */
    @PostMapping("lti13/dynamic-registration")
    @EnforceAdmin
    public ResponseEntity<Void> lti13DynamicRegistration(@RequestParam(name = "openid_configuration") String openIdConfiguration,
            @RequestParam(name = "registration_token", required = false) String registrationToken) {

        authCheckService.checkIsAdminElseThrow(null);
        ltiDynamicRegistrationService.performDynamicRegistration(openIdConfiguration, registrationToken);
        return ResponseEntity.ok().build();
    }

}
