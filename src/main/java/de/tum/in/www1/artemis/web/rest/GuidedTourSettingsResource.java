package de.tum.in.www1.artemis.web.rest;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.GuidedTourSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.user.UserService;

/**
 * Rest controller for managing GuidedTourSetting
 */
@RestController
public class GuidedTourSettingsResource {

    private final Logger log = LoggerFactory.getLogger(GuidedTourSettingsResource.class);

    private final UserService userService;

    public GuidedTourSettingsResource(UserService userService) {
        this.userService = userService;
    }

    /**
     * PUT /guided-tour-settings: update all guided tour settings of the current user
     * @param guidedTourSettings updated guided tour object
     * @return the guided tour settings
     */
    @PutMapping("/guided-tour-settings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<GuidedTourSetting>> updateGuidedTourSettings(@RequestBody Set<GuidedTourSetting> guidedTourSettings) {
        log.debug("REST request to update GuidedTourSetting : {}", guidedTourSettings);
        User currentUser = userService.updateGuidedTourSettings(guidedTourSettings);
        return new ResponseEntity<>(currentUser.getGuidedTourSettings(), null, HttpStatus.OK);
    }

    /**
     * DELETE /guided-tour-settings/:settingsKey : delete guided tour setting of the current user
     * @param settingsKey the guided tour settings key that of the setting that should be deleted
     * @return the guided tour settings
     */
    @DeleteMapping("/guided-tour-settings/{settingsKey}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<GuidedTourSetting>> deleteGuidedTourSetting(@PathVariable String settingsKey) {
        log.debug("REST request to delete GuidedTourSetting : {}", settingsKey);
        // Note: there is no explicit permission check here, because every user can delete the guided tour settings, e.g. by restarting a tutorial
        User currentUser = userService.deleteGuidedTourSetting(settingsKey);
        return new ResponseEntity<>(currentUser.getGuidedTourSettings(), null, HttpStatus.OK);
    }
}
