package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.EmailNotificationSetting;
import de.tum.cit.aet.artemis.communication.domain.EmailNotificationType;
import de.tum.cit.aet.artemis.communication.repository.EmailNotificationSettingRepository;
import de.tum.cit.aet.artemis.core.domain.User;

@Profile(PROFILE_CORE)
@Service
@Transactional
public class EmailNotificationSettingService {

    private final EmailNotificationSettingRepository emailNotificationSettingRepository;

    public EmailNotificationSettingService(EmailNotificationSettingRepository emailNotificationSettingRepository) {
        this.emailNotificationSettingRepository = emailNotificationSettingRepository;
    }

    public List<EmailNotificationSetting> getUserSettings(Long userId) {
        return emailNotificationSettingRepository.findByUserId(userId);
    }

    public Optional<EmailNotificationSetting> getUserSetting(Long userId, EmailNotificationType notificationType) {
        return emailNotificationSettingRepository.findByUserIdAndNotificationType(userId, notificationType);
    }

    public EmailNotificationSetting createOrUpdateSetting(User user, EmailNotificationType notificationType, boolean enabled) {
        Optional<EmailNotificationSetting> existingSetting = emailNotificationSettingRepository.findByUserIdAndNotificationType(user.getId(), notificationType);

        EmailNotificationSetting setting;
        if (existingSetting.isPresent()) {
            setting = existingSetting.get();
            setting.setEnabled(enabled);
        }
        else {
            setting = new EmailNotificationSetting();
            setting.setUser(user);
            setting.setNotificationType(notificationType);
            setting.setEnabled(enabled);
        }

        return emailNotificationSettingRepository.save(setting);
    }

    public boolean isNotificationEnabled(Long userId, EmailNotificationType notificationType) {
        // Default to true if no setting exists
        return emailNotificationSettingRepository.findByUserIdAndNotificationType(userId, notificationType).map(EmailNotificationSetting::getEnabled).orElse(true);
    }
}
