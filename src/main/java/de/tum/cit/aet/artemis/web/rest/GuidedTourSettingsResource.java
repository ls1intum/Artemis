package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.domain.GuidedTourSetting;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.service.user.UserService;

/**
 * Rest controller for managing GuidedTourSetting
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class GuidedTourSettingsResource {

    private static final Logger log = LoggerFactory.getLogger(GuidedTourSettingsResource.class);

    private final UserService userService;

    public GuidedTourSettingsResource(UserService userService) {
        this.userService = userService;
    }

    /**
     * PUT /guided-tour-settings: update all guided tour settings of the current user
     *
     * @param guidedTourSettings updated guided tour object
     * @return the guided tour settings
     */
    @PutMapping("guided-tour-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<GuidedTourSetting>> updateGuidedTourSettings(@RequestBody Set<GuidedTourSetting> guidedTourSettings) {
        log.debug("REST request to update GuidedTourSetting : {}", guidedTourSettings);
        User currentUser = userService.updateGuidedTourSettings(guidedTourSettings);
        return new ResponseEntity<>(currentUser.getGuidedTourSettings(), null, HttpStatus.OK);
    }

    /**
     * DELETE /guided-tour-settings/:settingsKey : delete guided tour setting of the current user
     *
     * @param settingsKey the guided tour settings key that of the setting that should be deleted
     * @return the guided tour settings
     */
    @DeleteMapping("guided-tour-settings/{settingsKey}")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<GuidedTourSetting>> deleteGuidedTourSetting(@PathVariable String settingsKey) {
        log.debug("REST request to delete GuidedTourSetting : {}", settingsKey);
        // Note: there is no explicit permission check here, because every user can delete the guided tour settings, e.g. by restarting a tutorial
        User currentUser = userService.deleteGuidedTourSetting(settingsKey);
        return new ResponseEntity<>(currentUser.getGuidedTourSettings(), null, HttpStatus.OK);
    }
}
