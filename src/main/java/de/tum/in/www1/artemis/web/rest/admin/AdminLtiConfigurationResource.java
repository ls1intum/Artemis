package de.tum.in.www1.artemis.web.rest.admin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.LtiPlatformConfiguration;
import de.tum.in.www1.artemis.repository.LtiPlatformConfigurationRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.lti.LtiDynamicRegistrationService;

@RestController
@RequestMapping("api/admin/")
@Profile("lti")
public class AdminLtiConfigurationResource {

    private final Logger log = LoggerFactory.getLogger(AdminLtiConfigurationResource.class);

    private final LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository;

    private final LtiDynamicRegistrationService ltiDynamicRegistrationService;

    private final AuthorizationCheckService authCheckService;

    public AdminLtiConfigurationResource(LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository, LtiDynamicRegistrationService ltiDynamicRegistrationService,
            AuthorizationCheckService authCheckService) {
        this.ltiPlatformConfigurationRepository = ltiPlatformConfigurationRepository;
        this.ltiDynamicRegistrationService = ltiDynamicRegistrationService;
        this.authCheckService = authCheckService;
    }

    /**
     * GET organizations : Get all configured lti platforms
     *
     * @return ResponseEntity containing a list of all lti platforms with status 200 (OK)
     */
    @GetMapping("ltiplatforms")
    @EnforceAdmin
    public ResponseEntity<List<LtiPlatformConfiguration>> getAllConfiguredLtiPlatforms() {
        log.debug("REST request to get all configured lti platforms");
        List<LtiPlatformConfiguration> platforms = ltiPlatformConfigurationRepository.findAll();
        return new ResponseEntity<>(platforms, HttpStatus.OK);
    }

    @PostMapping("/lti13/dynamic-registration")
    @EnforceAdmin
    public void lti13DynamicRegistration(@RequestParam(name = "openid_configuration") String openIdConfiguration,
            @RequestParam(name = "registration_token", required = false) String registrationToken) {

        authCheckService.checkIsAdminElseThrow(null);
        ltiDynamicRegistrationService.performDynamicRegistration(openIdConfiguration, registrationToken);
    }

}
