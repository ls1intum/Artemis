package de.tum.in.www1.artemis.web.rest.admin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.LtiPlatformConfiguration;
import de.tum.in.www1.artemis.repository.LtiPlatformConfigurationRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;

@RestController
@RequestMapping("api/admin/")
public class AdminLtiConfigurationResource {

    private final Logger log = LoggerFactory.getLogger(AdminLtiConfigurationResource.class);

    private final LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository;

    public AdminLtiConfigurationResource(LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository) {
        this.ltiPlatformConfigurationRepository = ltiPlatformConfigurationRepository;
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

}
