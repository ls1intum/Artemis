package de.tum.in.www1.artemis.web.rest.admin.iris;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.iris.settings.IrisSettings;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.iris.IrisSettingsService;

/**
 * REST controller for managing {@link IrisSettings}.
 */
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
        var updatedSettings = irisSettingsService.saveGlobalIrisSettings(settings);
        return ResponseEntity.ok(updatedSettings);
    }
}
