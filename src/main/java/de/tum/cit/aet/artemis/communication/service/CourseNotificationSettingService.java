package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationSettingSpecification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotification;
import de.tum.cit.aet.artemis.communication.domain.setting_presets.DefaultUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationSettingInfoDTO;
import de.tum.cit.aet.artemis.communication.repository.UserCourseNotificationSettingPresetRepository;
import de.tum.cit.aet.artemis.communication.repository.UserCourseNotificationSettingSpecificationRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Service responsible for handling course notification settings and filtering recipients based on their preferences.
 * This class manages the application of notification preferences on a per-user, per-course basis.
 */
@Profile(PROFILE_CORE)
@Service
public class CourseNotificationSettingService {

    private final CourseNotificationRegistryService courseNotificationRegistryService;

    private final CourseNotificationCacheService courseNotificationCacheService;

    private final UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository;

    private final UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository;

    private final CourseNotificationSettingPresetRegistryService courseNotificationSettingPresetRegistryService;

    public CourseNotificationSettingService(CourseNotificationRegistryService courseNotificationRegistryService, CourseNotificationCacheService courseNotificationCacheService,
            UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository,
            UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository,
            CourseNotificationSettingPresetRegistryService courseNotificationSettingPresetRegistryService) {
        this.courseNotificationRegistryService = courseNotificationRegistryService;
        this.courseNotificationCacheService = courseNotificationCacheService;
        this.userCourseNotificationSettingSpecificationRepository = userCourseNotificationSettingSpecificationRepository;
        this.userCourseNotificationSettingPresetRepository = userCourseNotificationSettingPresetRepository;
        this.courseNotificationSettingPresetRegistryService = courseNotificationSettingPresetRegistryService;
    }

    /**
     * Applies a notification setting preset to a user's course settings.
     *
     * <p>
     * This method changes the user's notification preferences for a specific course according to the selected preset.
     * If the selected preset ID is the same as the current one, no action is taken.
     * </p>
     *
     * <p>
     * When the preset ID is 0 (representing "Custom" settings), the method copies the current notification settings
     * as individual specifications to allow for future customization. For any other preset ID, existing custom
     * specifications are removed since they will be dynamically determined by the preset.
     * </p>
     *
     * @param selectedPresetId the ID of the notification preset to apply
     * @param userId           the ID of the user whose settings are being updated
     * @param courseId         the ID of the course for which notification settings are being updated
     */
    public void applyPreset(short selectedPresetId, long userId, long courseId) {
        var currentPresetEntity = userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(userId, courseId);

        var course = new Course();
        course.setId(courseId);
        var user = new User(userId);

        if (currentPresetEntity == null) {
            currentPresetEntity = new UserCourseNotificationSettingPreset(user, course,
                    courseNotificationSettingPresetRegistryService.getPresetId(DefaultUserCourseNotificationSettingPreset.class).shortValue());
        }

        if (Objects.equals(selectedPresetId, currentPresetEntity.getSettingPreset())) {
            return;
        }

        var currentPreset = courseNotificationSettingPresetRegistryService.getPresetById(currentPresetEntity.getSettingPreset());

        currentPresetEntity.setSettingPreset(selectedPresetId);
        userCourseNotificationSettingPresetRepository.save(currentPresetEntity);

        // Preset id 0 means "Custom" and we need to "copy" the settings from the current preset
        if (selectedPresetId == 0) {
            List<UserCourseNotificationSettingSpecification> specifications = new ArrayList<>();

            currentPreset.getPresetMap().forEach((key, value) -> {
                var notificationTypeId = courseNotificationRegistryService.getNotificationIdentifier(key);

                specifications.add(new UserCourseNotificationSettingSpecification(user, course, notificationTypeId, value.get(NotificationChannelOption.EMAIL),
                        value.get(NotificationChannelOption.PUSH), value.get(NotificationChannelOption.WEBAPP), false));
            });

            userCourseNotificationSettingSpecificationRepository.saveAll(specifications);
        }
        else {
            var specifications = userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(userId, courseId);

            userCourseNotificationSettingSpecificationRepository.deleteAll(specifications);
        }

        courseNotificationCacheService.invalidateCourseNotificationSettingSpecificationCacheForUser(userId, courseId);
    }

    /**
     * Applies notification specifications for a user in a specific course.
     *
     * <p>
     * This method first applies the default "custom" preset (id=0) for the user and course,
     * then creates or updates notification specifications for each specified notification type.
     * For new specifications, the notification channels are set based on the provided channel option.
     * </p>
     *
     * @param notificationTypeChannels A list of notification type IDs matched to a specific channel option
     * @param userId                   The ID of the user for whom the specification will be applied
     * @param courseId                 The ID of the course for which the specification will be applied
     */
    public void applySpecification(Map<Short, Map<NotificationChannelOption, Boolean>> notificationTypeChannels, long userId, long courseId) {
        // First we apply the "custom" setting preset
        applyPreset((short) 0, userId, courseId);

        var specifications = userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseIdAndCourseNotificationTypeIn(userId, courseId,
                notificationTypeChannels.keySet().stream().toList());
        var specificationEntities = new ArrayList<UserCourseNotificationSettingSpecification>();

        notificationTypeChannels.forEach((notificationTypeId, channel) -> {
            if (courseNotificationRegistryService.getNotificationClass(notificationTypeId) == null) {
                return;
            }

            var specificationOptional = specifications.stream().filter((spec) -> Objects.equals(spec.getCourseNotificationType(), notificationTypeId)).findFirst();
            UserCourseNotificationSettingSpecification specification;

            if (specificationOptional.isEmpty()) {
                var course = new Course();
                course.setId(courseId);
                var user = new User(userId);
                specification = new UserCourseNotificationSettingSpecification(user, course, notificationTypeId, channel.getOrDefault(NotificationChannelOption.EMAIL, false),
                        channel.getOrDefault(NotificationChannelOption.PUSH, false), channel.getOrDefault(NotificationChannelOption.WEBAPP, false), false);
            }
            else {
                specification = specificationOptional.get();

                if (channel.get(NotificationChannelOption.EMAIL) != null) {
                    specification.setEmail(channel.get(NotificationChannelOption.EMAIL));
                }

                if (channel.get(NotificationChannelOption.PUSH) != null) {
                    specification.setPush(channel.get(NotificationChannelOption.PUSH));
                }

                if (channel.get(NotificationChannelOption.WEBAPP) != null) {
                    specification.setWebapp(channel.get(NotificationChannelOption.WEBAPP));
                }
            }

            specificationEntities.add(specification);
        });

        userCourseNotificationSettingSpecificationRepository.saveAll(specificationEntities);
        courseNotificationCacheService.invalidateCourseNotificationSettingSpecificationCacheForUser(userId, courseId);
    }

    /**
     * Retrieves the notification setting information for a specific user in a specific course.
     *
     * <p>
     * This method fetches the user's current notification setting preset for the given course.
     * If no preset exists, it creates a default one using the DefaultUserCourseNotificationSettingPreset.
     * The method then transforms the notification settings from the preset into a map of
     * notification type identifiers to channel options.
     * </p>
     *
     * <p>
     * For custom presets (indicated by settingPreset == 0), the method applies any user-specific
     * notification setting overrides from the database.
     * </p>
     *
     * @param userId   The ID of the user whose notification settings are being retrieved
     * @param courseId The ID of the course for which to retrieve notification settings
     * @return A CourseNotificationSettingInfoDTO containing the preset ID and a map of
     *         notification type identifiers to their channel configurations
     */
    public CourseNotificationSettingInfoDTO getSettingInfo(long userId, long courseId) {
        var course = new Course();
        course.setId(courseId);
        var user = new User(userId);
        short presetId;
        short defaultPresetId = courseNotificationSettingPresetRegistryService.getPresetId(DefaultUserCourseNotificationSettingPreset.class).shortValue();
        var currentPresetEntity = userCourseNotificationSettingPresetRepository.findUserCourseNotificationSettingPresetByUserIdAndCourseId(userId, courseId);

        if (currentPresetEntity != null) {
            presetId = currentPresetEntity.getSettingPreset();
        }
        else {
            presetId = defaultPresetId;
        }

        if (currentPresetEntity == null || currentPresetEntity.getSettingPreset() == 0) {
            currentPresetEntity = new UserCourseNotificationSettingPreset(user, course, defaultPresetId);
        }

        var currentPreset = courseNotificationSettingPresetRegistryService.getPresetById(currentPresetEntity.getSettingPreset());

        Map<Short, Map<NotificationChannelOption, Boolean>> notificationTypeChannels = new HashMap<>();

        for (var entry : currentPreset.getPresetMap().entrySet()) {
            notificationTypeChannels.put(courseNotificationRegistryService.getNotificationIdentifier(entry.getKey()), entry.getValue());
        }

        if (presetId == 0) {
            var specifications = userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(userId, courseId);

            // If custom is specified, we want to overwrite the settings that are present in the database. Note that not all may be present.
            specifications.forEach(specification -> {
                notificationTypeChannels.put(specification.getCourseNotificationType(), Map.of(NotificationChannelOption.EMAIL, specification.isEmail(),
                        NotificationChannelOption.PUSH, specification.isPush(), NotificationChannelOption.WEBAPP, specification.isWebapp()));
            });
        }

        return new CourseNotificationSettingInfoDTO(presetId, notificationTypeChannels);
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
    protected List<User> filterRecipientsBy(CourseNotification notification, List<User> recipients, NotificationChannelOption filterFor) {
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
