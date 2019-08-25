package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.GuidedTourSettings;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.UserService;

/**
 * Rest controller for managing GuidedTourSettings
 */
@RestController
@RequestMapping("/api")
public class GuidedTourSettingsResource {

    private final Logger log = LoggerFactory.getLogger(GuidedTourSettingsResource.class);

    private static final String ENTITY_NAME = "guidedTourSettings";

    private final UserService userService;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    public GuidedTourSettingsResource(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /guided-tour-settings: get all guided tour settings of the current user
     *
     * @return the guided tour settings
     */
    @GetMapping("/guided-tour-settings")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<GuidedTourSettings>> getGuidedTourSettings() {
        log.debug("REST request to get all guided tour settings of the current user");
        User currentUser = userService.getUser();
        return new ResponseEntity<>(currentUser.getGuidedTourSettings(), null, HttpStatus.OK);
    }

    /**
     * PUT /guided-tour-settings: update all guided tour settings of the current user
     * @param guidedTourSettings updated guided tour object
     * @return the guided tour settings
     */
    @PutMapping("/guided-tour-settings")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<List<GuidedTourSettings>> updateGuidedTourSettings(@RequestBody List<GuidedTourSettings> guidedTourSettings) {
        log.debug("REST request to update GuidedTourSettings : {}", guidedTourSettings);
        User currentUser = userService.updateGuidedTourSettings(guidedTourSettings);
        return new ResponseEntity<>(currentUser.getGuidedTourSettings(), null, HttpStatus.OK);
    }
}
