package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationSetting;
import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.communication.dto.UpdateGlobalNotificationSettingDTO;
import de.tum.cit.aet.artemis.communication.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.communication.service.GlobalNotificationSettingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/communication/")
@Lazy
public class GlobalNotificationSettingResource {

    private final GlobalNotificationSettingService globalNotificationSettingService;

    private final GlobalNotificationSettingRepository globalNotificationSettingRepository;

    private final UserRepository userRepository;

    /**
     * Creates a new {@code GlobalNotificationSettingResource}.
     *
     * @param globalNotificationSettingService    business service used to create, update and read settings
     * @param globalNotificationSettingRepository repository used to fetch global notification settings
     * @param userRepository                      repository used to fetch the currently authenticated {@link User}
     */
    public GlobalNotificationSettingResource(GlobalNotificationSettingService globalNotificationSettingService,
            GlobalNotificationSettingRepository globalNotificationSettingRepository, UserRepository userRepository) {
        this.globalNotificationSettingService = globalNotificationSettingService;
        this.globalNotificationSettingRepository = globalNotificationSettingRepository;
        this.userRepository = userRepository;
    }

    /**
     * {@code PUT /global-notification-settings/{notificationType}} : Update (or create) the
     * {@link GlobalNotificationSetting} for the given {@code notificationType}.
     *
     * @param notificationType the name of the {@link GlobalNotificationType};
     * @param request          the JSON request body, e.g. {@code {"enabled":true}}
     * @return {@link ResponseEntity} containing the persisted setting and HTTP 200 on success;
     *         HTTP 400 if the body is missing the {@code enabled} property or if {@code notificationType} is unknown
     */
    @PutMapping("global-notification-settings/{notificationType}")
    @EnforceAtLeastStudent
    public ResponseEntity<GlobalNotificationSetting> updateSetting(@PathVariable GlobalNotificationType notificationType, @RequestBody UpdateGlobalNotificationSettingDTO request) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        boolean enabled = request.enabled();
        return ResponseEntity.ok(globalNotificationSettingService.createOrUpdateSetting(user, notificationType, enabled));
    }

    /**
     * {@code GET /global-notification-settings} : Retrieves the global notification settings for the currently logged-in user.
     *
     * @return {@link ResponseEntity} with HTTP status 200 (OK) and a map containing each {@link GlobalNotificationType}
     *         as a key (using {@code name()}) and a boolean value indicating whether notifications of that type are enabled.
     *         If a specific type has no stored setting, it defaults to {@code true}.
     */
    @GetMapping("global-notification-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<Map<String, Boolean>> getAllSettings() {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Map<String, Boolean> result = globalNotificationSettingRepository.getAllSettingsAsMap(user.getId());
        return ResponseEntity.ok(result);
    }
}
