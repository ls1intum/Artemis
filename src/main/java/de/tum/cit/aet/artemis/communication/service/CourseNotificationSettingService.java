package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationSettingSpecification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotification;
import de.tum.cit.aet.artemis.communication.repository.UserCourseNotificationSettingPresetRepository;
import de.tum.cit.aet.artemis.communication.repository.UserCourseNotificationSettingSpecificationRepository;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Service responsible for handling course notification settings and filtering recipients based on their preferences.
 * This class manages the application of notification preferences on a per-user, per-course basis.
 */
@Profile(PROFILE_CORE)
@Service
public class CourseNotificationSettingService {

    private final CourseNotificationRegistryService courseNotificationRegistryService;

    private final UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository;

    private final UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository;

    private final CourseNotificationSettingPresetRegistryService courseNotificationSettingPresetRegistryService;

    public CourseNotificationSettingService(CourseNotificationRegistryService courseNotificationRegistryService,
            UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository,
            UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository,
            CourseNotificationSettingPresetRegistryService courseNotificationSettingPresetRegistryService) {
        this.courseNotificationRegistryService = courseNotificationRegistryService;
        this.userCourseNotificationSettingSpecificationRepository = userCourseNotificationSettingSpecificationRepository;
        this.userCourseNotificationSettingPresetRepository = userCourseNotificationSettingPresetRepository;
        this.courseNotificationSettingPresetRegistryService = courseNotificationSettingPresetRegistryService;
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
    protected List<User> filterRecipientsBy(CourseNotification notification, List<User> recipients, NotificationSettingOption filterFor) {
        return recipients.stream().filter((recipient) -> {
            // Note: We run a single query per user, however, this query is cached, so this should not cause performance issues.
            var preset = userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(recipient.getId(), notification.courseId);

            if (preset == null) {
                // Run query on default preset if none are present
                return this.courseNotificationSettingPresetRegistryService.isPresetSettingEnabled(1, notification.getClass(), filterFor);
            }
            else if (preset.getSettingPreset() == 0) {
                // The specifications are cached per-user per-course. Similar to above.
                var specifications = userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(recipient.getId(), notification.courseId);

                var specification = specifications.stream().filter(
                        (spec) -> Objects.equals(spec.getCourseNotificationType(), this.courseNotificationRegistryService.getNotificationIdentifier(notification.getClass())))
                        .findFirst();

                return specification.map(switch (filterFor) {
                    case WEBAPP -> UserCourseNotificationSettingSpecification::isWebapp;
                    case PUSH -> UserCourseNotificationSettingSpecification::isPush;
                    case EMAIL -> UserCourseNotificationSettingSpecification::isEmail;
                }).orElse(false);
            }
            else {
                return this.courseNotificationSettingPresetRegistryService.isPresetSettingEnabled(preset.getSettingPreset(), notification.getClass(), filterFor);
            }
        }).toList();
    }
}
