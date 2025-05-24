package de.tum.cit.aet.artemis.communication.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.EmailNotificationSetting;
import de.tum.cit.aet.artemis.communication.domain.EmailNotificationType;
import de.tum.cit.aet.artemis.communication.service.EmailNotificationSettingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@RestController
@RequestMapping("/api/communication")
public class EmailNotificationSettingResource {

    private final EmailNotificationSettingService emailNotificationSettingService;

    private final UserRepository userRepository;

    /**
     * Creates a new {@code EmailNotificationSettingResource}.
     *
     * @param emailNotificationSettingService business service used to create, update and read settings
     * @param userRepository                  repository used to fetch the currently authenticated {@link User}
     */
    public EmailNotificationSettingResource(EmailNotificationSettingService emailNotificationSettingService, UserRepository userRepository) {
        this.emailNotificationSettingService = emailNotificationSettingService;
        this.userRepository = userRepository;
    }

    /**
     * {@code PUT /email-notification-settings/{notificationType}} : Update (or create) the
     * {@link EmailNotificationSetting} for the given {@code notificationType}.
     *
     * @param notificationType the name of the {@link EmailNotificationType}; case‑insensitive
     * @param request          the JSON request body, e.g. {@code {"enabled":true}}
     * @return {@link ResponseEntity} containing the persisted setting and HTTP 200 on success;<br>
     *         HTTP 400 if the body is missing the {@code enabled} property or if {@code notificationType} is unknown
     */
    @PutMapping("email-notification-settings/{notificationType}")
    public ResponseEntity<EmailNotificationSetting> updateSetting(@PathVariable String notificationType, @RequestBody Map<String, Boolean> request) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Boolean enabled = request.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            EmailNotificationType type = EmailNotificationType.valueOf(notificationType.toUpperCase());
            return ResponseEntity.ok(emailNotificationSettingService.createOrUpdateSetting(user, type, enabled));
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * {@code GET  /email-notification-settings} : Return the enabled/disabled state of each
     * {@link EmailNotificationType} for the current user.
     *
     * @return {@link ResponseEntity} with HTTP 200 and a map where the key is the {@code name()} of the type
     *         and the value indicates whether e‑mails of this type are enabled.
     *         Types without an explicit setting default to {@code true}.
     */
    @GetMapping("email-notification-settings")
    public ResponseEntity<Map<String, Boolean>> getAllSettings() {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        List<EmailNotificationSetting> settings = emailNotificationSettingService.getUserSettings(user.getId());
        Map<String, Boolean> result = new HashMap<>();
        for (EmailNotificationType type : EmailNotificationType.values()) {
            // Default to true if not set
            boolean enabled = settings.stream().filter(s -> s.getNotificationType() == type).findFirst().map(EmailNotificationSetting::getEnabled).orElse(true);
            result.put(type.name(), enabled);
        }
        return ResponseEntity.ok(result);
    }
}
