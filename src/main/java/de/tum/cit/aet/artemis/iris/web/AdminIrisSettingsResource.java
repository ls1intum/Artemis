package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSettings;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

/**
 * REST controller for managing {@link IrisSettings}.
 */
@Profile(PROFILE_IRIS)
@RestController
@RequestMapping("api/admin/")
public class AdminIrisSettingsResource {

    private final IrisSettingsService irisSettingsService;

    public AdminIrisSettingsResource(IrisSettingsService irisSettingsService) {
        this.irisSettingsService = irisSettingsService;
    }

    /**
     * PUT iris/global-iris-settings: Update the global iris settings.
     *
     * @param settings the settings to update
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated settings.
     */
    @PutMapping("iris/global-iris-settings")
    @EnforceAdmin
    public ResponseEntity<IrisSettings> updateGlobalSettings(@RequestBody IrisSettings settings) {
        var updatedSettings = irisSettingsService.saveIrisSettings(settings);
        return ResponseEntity.ok(updatedSettings);
    }
}
