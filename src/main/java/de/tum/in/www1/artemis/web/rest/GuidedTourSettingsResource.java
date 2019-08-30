package de.tum.in.www1.artemis.web.rest;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.GuidedTourSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.UserService;

/**
 * Rest controller for managing GuidedTourSetting
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
     * PUT /guided-tour-settings: update all guided tour settings of the current user
     * @param guidedTourSettings updated guided tour object
     * @return the guided tour settings
     */
    @PutMapping("/guided-tour-settings")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<Set<GuidedTourSetting>> updateGuidedTourSettings(@RequestBody Set<GuidedTourSetting> guidedTourSettings) {
        log.debug("REST request to update GuidedTourSetting : {}", guidedTourSettings);
        User currentUser = userService.updateGuidedTourSettings(guidedTourSettings);
        return new ResponseEntity<>(currentUser.getGuidedTourSettings(), null, HttpStatus.OK);
    }
}
