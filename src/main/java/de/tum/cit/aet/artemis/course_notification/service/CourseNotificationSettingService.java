package de.tum.cit.aet.artemis.course_notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.course_notification.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.course_notification.domain.UserCourseNotificationSettingSpecification;
import de.tum.cit.aet.artemis.course_notification.domain.notifications.CourseNotification;
import de.tum.cit.aet.artemis.course_notification.repository.UserCourseNotificationSettingPresetRepository;
import de.tum.cit.aet.artemis.course_notification.repository.UserCourseNotificationSettingSpecificationRepository;

/**
 * Service responsible for handling course notification settings and filtering recipients based on their preferences.
 * This class manages the application of notification preferences on a per-user, per-course basis.
 */
@Profile(PROFILE_CORE)
@Service
public class CourseNotificationSettingService {

    private final CourseNotificationRegistry courseNotificationRegistry;

    private final UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository;

    private final UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository;

    private final CourseNotificationSettingPresetRegistry courseNotificationSettingPresetRegistry;

    /**
     * Constructs a new CourseNotificationSettingService with required dependencies.
     *
     * @param courseNotificationRegistry                           Registry for managing course notification types
     * @param userCourseNotificationSettingSpecificationRepository Repository for user-specific notification settings
     * @param userCourseNotificationSettingPresetRepository        Repository for user notification presets
     * @param courseNotificationSettingPresetRegistry              Registry for standard notification setting presets
     */
    public CourseNotificationSettingService(CourseNotificationRegistry courseNotificationRegistry,
            UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository,
            UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository,
            CourseNotificationSettingPresetRegistry courseNotificationSettingPresetRegistry) {
        this.courseNotificationRegistry = courseNotificationRegistry;
        this.userCourseNotificationSettingSpecificationRepository = userCourseNotificationSettingSpecificationRepository;
        this.userCourseNotificationSettingPresetRepository = userCourseNotificationSettingPresetRepository;
        this.courseNotificationSettingPresetRegistry = courseNotificationSettingPresetRegistry;
    }

    /**
     * Private helper method that performs the actual filtering of recipients based on notification type.
     * This method checks user presets first. If a user has custom settings (preset 0), it looks up their
     * specific notification preferences. Otherwise, it uses the preset registry.
     *
     * @param notification The course notification to be sent
     * @param recipients   List of potential recipients
     * @param filterFor    The notification channel to filter for (WEBAPP, PUSH, or EMAIL)
     * @return Filtered list of users who have enabled notifications for the specified channel
     */
    public List<User> filterRecipientsBy(CourseNotification notification, List<User> recipients, NotificationSettingOption filterFor) {
        return recipients.stream().filter((recipient) -> {
            // Note: We run a single query per user, however, this query is cached, so this should not cause performance issues.
            var preset = userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(recipient.getId(), notification.courseId);

            if (preset.getSettingPreset() == 0) {
                // The specifications are cached per-user per-course. Similar to above.
                var specifications = userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(recipient.getId(), notification.courseId);

                var specification = specifications.stream()
                        .filter((spec) -> Objects.equals(spec.getCourseNotificationType(), this.courseNotificationRegistry.getNotificationIdentifier(notification.getClass())))
                        .findFirst();

                return specification.map(switch (filterFor) {
                    case WEBAPP -> UserCourseNotificationSettingSpecification::isWebapp;
                    case PUSH -> UserCourseNotificationSettingSpecification::isPush;
                    case EMAIL -> UserCourseNotificationSettingSpecification::isEmail;
                }).orElse(false);
            }
            else {
                return this.courseNotificationSettingPresetRegistry.isPresetSettingEnabled(preset.getSettingPreset(), notification.getClass(), filterFor);
            }
        }).toList();
    }
}
