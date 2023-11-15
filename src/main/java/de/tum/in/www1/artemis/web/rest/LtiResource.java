package de.tum.in.www1.artemis.web.rest;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.lti.LtiDynamicRegistrationService;

/**
 * REST controller to handle LTI10 launches.
 */
@RestController
@RequestMapping("/api")
@Profile("lti")
public class LtiResource {

    private final LtiDynamicRegistrationService ltiDynamicRegistrationService;

    private final AuthorizationCheckService authCheckService;

    public static final String LOGIN_REDIRECT_CLIENT_PATH = "/lti/launch";

    public LtiResource(LtiDynamicRegistrationService ltiDynamicRegistrationService, AuthorizationCheckService authCheckService) {
        this.ltiDynamicRegistrationService = ltiDynamicRegistrationService;
        this.authCheckService = authCheckService;
    }

    @PostMapping("/lti13/dynamic-registration")
    @EnforceAdmin
    public void lti13DynamicRegistration(@RequestParam(name = "openid_configuration") String openIdConfiguration,
            @RequestParam(name = "registration_token", required = false) String registrationToken) {

        authCheckService.checkIsAdminElseThrow(null);
        ltiDynamicRegistrationService.performDynamicRegistration(openIdConfiguration, registrationToken);
    }
}
