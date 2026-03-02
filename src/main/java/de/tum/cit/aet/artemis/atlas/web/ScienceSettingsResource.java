package de.tum.cit.aet.artemis.atlas.web;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceSetting;
import de.tum.cit.aet.artemis.atlas.dto.ScienceSettingDTO;
import de.tum.cit.aet.artemis.atlas.repository.ScienceSettingRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;

/**
 * REST controller for managing ScienceSettings.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@RestController
@RequestMapping("api/atlas/")
public class ScienceSettingsResource {

    private static final Logger log = LoggerFactory.getLogger(ScienceSettingsResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserRepository userRepository;

    private final ScienceSettingRepository scienceSettingRepository;

    public ScienceSettingsResource(UserRepository userRepository, ScienceSettingRepository scienceSettingRepository) {
        this.userRepository = userRepository;
        this.scienceSettingRepository = scienceSettingRepository;
    }

    /**
     * GET science-settings : Get all ScienceSettings for current user
     * <p>
     * Fetches the ScienceSettings for the current user from the server.
     *
     * @return the list of found ScienceSettings
     */
    @GetMapping("science-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<ScienceSettingDTO>> getScienceSettingsForCurrentUser() {
        User user = userRepository.getUser();
        log.debug("REST request to get all ScienceSettings for current user {}", user);
        Set<ScienceSetting> scienceSettings = scienceSettingRepository.findAllByUserId(user.getId());
        var scienceSettingDtos = scienceSettings.stream().map(ScienceSettingDTO::of).collect(Collectors.toSet());
        return new ResponseEntity<>(scienceSettingDtos, HttpStatus.OK);
    }

    /**
     * PUT science-settings : Save ScienceSettings for current user
     * <p>
     * Saves the provided ScienceSettings to the server.
     *
     * @param scienceSettings which should be saved to the scienceSetting database.
     * @return the ScienceSettings that just got saved for the current user as array
     *         200 for a successful execution, 400 if the user provided empty settings to save, 500 if the save call returns empty settings
     */
    @PutMapping("science-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<ScienceSettingDTO[]> saveScienceSettingsForCurrentUser(@NonNull @RequestBody ScienceSettingDTO[] scienceSettings) {
        if (scienceSettings.length == 0) {
            throw new BadRequestAlertException("Cannot save non-existing Science Settings", "ScienceSettings", "scienceSettingsEmpty");
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to save ScienceSettings : {} for current user {}", scienceSettings, user);
        List<ScienceSetting> scienceSettingList = Arrays.stream(scienceSettings).map(dto -> new ScienceSetting(user, dto.settingId(), dto.active())).toList();
        List<ScienceSetting> persistedSettingList = scienceSettingRepository.saveAll(scienceSettingList);
        if (persistedSettingList.isEmpty()) {
            throw new BadRequestAlertException("Error occurred during saving of Science Settings", "ScienceSettings", "scienceSettingsEmptyAfterSave");
        }
        ScienceSettingDTO[] persistedSettingArray = persistedSettingList.stream().map(ScienceSettingDTO::of).toArray(ScienceSettingDTO[]::new);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, "scienceSetting", "test")).body(persistedSettingArray);
    }
}
