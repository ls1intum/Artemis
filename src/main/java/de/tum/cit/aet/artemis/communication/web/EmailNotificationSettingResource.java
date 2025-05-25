package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.EmailNotificationSetting;
import de.tum.cit.aet.artemis.communication.domain.EmailNotificationType;
import de.tum.cit.aet.artemis.communication.dto.UpdateEmailNotificationSettingDTO;
import de.tum.cit.aet.artemis.communication.repository.EmailNotificationSettingRepository;
import de.tum.cit.aet.artemis.communication.service.EmailNotificationSettingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/communication/")
public class EmailNotificationSettingResource {

    private final EmailNotificationSettingService emailNotificationSettingService;

    private final EmailNotificationSettingRepository emailNotificationSettingRepository;

    private final UserRepository userRepository;

    /**
     * Creates a new {@code EmailNotificationSettingResource}.
     *
     * @param emailNotificationSettingService business service used to create, update and read settings
     * @param userRepository                  repository used to fetch the currently authenticated {@link User}
     */
    public EmailNotificationSettingResource(EmailNotificationSettingService emailNotificationSettingService, EmailNotificationSettingRepository emailNotificationSettingRepository,
            UserRepository userRepository) {
        this.emailNotificationSettingService = emailNotificationSettingService;
        this.emailNotificationSettingRepository = emailNotificationSettingRepository;
        this.userRepository = userRepository;
    }

    /**
     * {@code PUT /email-notification-settings/{notificationType}} : Update (or create) the
     * {@link EmailNotificationSetting} for the given {@code notificationType}.
     *
     * @param notificationType the name of the {@link EmailNotificationType}; case‑insensitive
     * @param request          the JSON request body, e.g. {@code {"enabled":true}}
     * @return {@link ResponseEntity} containing the persisted setting and HTTP 200 on success;
     *         HTTP 400 if the body is missing the {@code enabled} property or if {@code notificationType} is unknown
     */
    @PutMapping("email-notification-settings/{notificationType}")
    @EnforceAtLeastStudent
    public ResponseEntity<EmailNotificationSetting> updateSetting(@PathVariable EmailNotificationType notificationType, @RequestBody UpdateEmailNotificationSettingDTO request) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        boolean enabled = request.enabled();
        try {
            return ResponseEntity.ok(emailNotificationSettingService.createOrUpdateSetting(user, notificationType, enabled));
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * {@code GET /email-notification-settings} : Retrieves the email notification settings for the currently logged-in user.
     *
     * @return {@link ResponseEntity} with HTTP status 200 (OK) and a map containing each {@link EmailNotificationType}
     *         as a key (using {@code name()}) and a boolean value indicating whether notifications of that type are enabled.
     *         If a specific type has no stored setting, it defaults to {@code true}.
     */
    @GetMapping("email-notification-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<Map<String, Boolean>> getAllSettings() {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Map<String, Boolean> result = emailNotificationSettingRepository.getAllSettingsAsMap(user.getId());
        return ResponseEntity.ok(result);
    }
}
