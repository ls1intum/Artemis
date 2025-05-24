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

    public EmailNotificationSettingResource(EmailNotificationSettingService emailNotificationSettingService, UserRepository userRepository) {
        this.emailNotificationSettingService = emailNotificationSettingService;
        this.userRepository = userRepository;
    }

    @GetMapping("/email-notification-settings")
    public ResponseEntity<List<EmailNotificationSetting>> getUserSettings() {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(emailNotificationSettingService.getUserSettings(user.getId()));
    }

    @PutMapping("/email-notification-settings/{notificationType}")
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

    @GetMapping("/email-notification-settings/all")
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
