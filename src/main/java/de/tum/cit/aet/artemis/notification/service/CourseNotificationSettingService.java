package de.tum.cit.aet.artemis.notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationSettingSpecification;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotification;
import de.tum.cit.aet.artemis.notification.domain.setting_presets.DefaultUserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationSettingInfoDTO;
import de.tum.cit.aet.artemis.notification.dto.UserCourseNotificationSettingPresetEntryDTO;
import de.tum.cit.aet.artemis.notification.dto.UserCourseNotificationSettingSpecificationEntryDTO;
import de.tum.cit.aet.artemis.notification.repository.UserCourseNotificationSettingPresetRepository;
import de.tum.cit.aet.artemis.notification.repository.UserCourseNotificationSettingSpecificationRepository;

/**
 * Service responsible for handling course notification settings and filtering recipients based on their preferences.
 * This class manages the application of notification preferences on a per-user, per-course basis.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class CourseNotificationSettingService {

    /**
     * The preset id that means "the user described their channels themselves", so their specification rows apply.
     */
    private static final short CUSTOM_PRESET_ID = 0;

    /**
     * The preset a user is treated as being on when they have never chosen one.
     */
    private static final short DEFAULT_PRESET_ID = 1;

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
        // The entity, not the cached preset value: this path writes the row back, so it needs its identity.
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
            var specifications = userCourseNotificationSettingSpecificationRepository.findAllEntitiesByUserIdAndCourseId(userId, courseId);

            userCourseNotificationSettingSpecificationRepository.deleteAll(specifications);
        }

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
        short defaultPresetId = courseNotificationSettingPresetRegistryService.getPresetId(DefaultUserCourseNotificationSettingPreset.class).shortValue();
        Short selectedPreset = userCourseNotificationSettingPresetRepository.findSettingPresetByUserIdAndCourseId(userId, courseId);
        short presetId = selectedPreset != null ? selectedPreset : defaultPresetId;
        // Preset id 0 means "Custom", which has no channels of its own: the defaults are the base the specifications
        // below are applied on top of.
        short presetToReadChannelsFrom = selectedPreset == null || selectedPreset == 0 ? defaultPresetId : selectedPreset;

        var currentPreset = courseNotificationSettingPresetRegistryService.getPresetById(presetToReadChannelsFrom);

        Map<Short, Map<NotificationChannelOption, Boolean>> notificationTypeChannels = new HashMap<>();

        for (var entry : currentPreset.getPresetMap().entrySet()) {
            notificationTypeChannels.put(courseNotificationRegistryService.getNotificationIdentifier(entry.getKey()), entry.getValue());
        }

        if (presetId == 0) {
            var specifications = userCourseNotificationSettingSpecificationRepository.findAllByUserIdAndCourseId(userId, courseId);

            // If custom is specified, we want to overwrite the settings that are present in the database. Note that not all may be present.
            specifications.forEach(specification -> notificationTypeChannels.put(specification.courseNotificationType(), Map.of(NotificationChannelOption.EMAIL,
                    specification.email(), NotificationChannelOption.PUSH, specification.push(), NotificationChannelOption.WEBAPP, specification.webapp())));
        }

        return new CourseNotificationSettingInfoDTO(presetId, notificationTypeChannels);
    }

    /**
     * The notification settings of a set of recipients in one course, read in advance so that filtering them does not
     * have to query per user.
     *
     * @param presets        the selected preset per user, absent for users who have none
     * @param specifications the specifications per user, only populated for users on a custom preset
     */
    protected record RecipientSettings(Map<Long, Short> presets, Map<Long, List<UserCourseNotificationSettingSpecificationEntryDTO>> specifications) {
    }

    /**
     * Reads the notification settings of a whole set of recipients in one go.
     * <p>
     * Sending a notification filters its recipients once per delivery channel, and filtering used to ask for each
     * recipient's settings individually: a course-wide announcement to 3000 students across three channels issued 9000
     * lookups. This reads the cohort instead, so the cost is one query - two if anybody is on a custom preset, since
     * only those users have specifications worth reading.
     *
     * @param courseId   the course the notification belongs to
     * @param recipients the users the notification may go to
     * @return their settings, for {@link #filterRecipientsBy} to apply
     */
    protected RecipientSettings loadSettingsFor(long courseId, List<User> recipients) {
        Set<Long> userIds = recipients.stream().map(User::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return new RecipientSettings(Map.of(), Map.of());
        }

        Map<Long, Short> presets = userCourseNotificationSettingPresetRepository.findSettingPresetsByUserIdsAndCourseId(userIds, courseId).stream()
                .collect(Collectors.toMap(UserCourseNotificationSettingPresetEntryDTO::userId, UserCourseNotificationSettingPresetEntryDTO::settingPreset));

        // Only a custom preset - the zero one - is described by specification rows, so nobody else needs reading.
        Set<Long> customPresetUserIds = presets.entrySet().stream().filter(entry -> entry.getValue() == CUSTOM_PRESET_ID).map(Map.Entry::getKey).collect(Collectors.toSet());
        if (customPresetUserIds.isEmpty()) {
            return new RecipientSettings(presets, Map.of());
        }

        Map<Long, List<UserCourseNotificationSettingSpecificationEntryDTO>> specifications = userCourseNotificationSettingSpecificationRepository
                .findAllByUserIdsAndCourseId(customPresetUserIds, courseId).stream().collect(Collectors.groupingBy(UserCourseNotificationSettingSpecificationEntryDTO::userId));

        return new RecipientSettings(presets, specifications);
    }

    /**
     * Private helper method that performs the actual filtering of recipients based on notification type.
     * This method checks user presets first. If a user has custom settings (preset 0), it looks up their
     * specific notification preferences. Otherwise, it uses the preset registry.
     *
     * @param notification The course notification to be sent
     * @param recipients   List of potential recipients
     * @param filterFor    The notification channel to filter for (WEBAPP, PUSH, or EMAIL)
     * @param settings     The recipients' settings, read once by {@link #loadSettingsFor}
     * @return Filtered list of users who have enabled notifications for the specified channel
     */
    protected List<User> filterRecipientsBy(CourseNotification notification, List<User> recipients, NotificationChannelOption filterFor, RecipientSettings settings) {
        Short notificationType = this.courseNotificationRegistryService.getNotificationIdentifier(notification.getClass());
        return recipients.stream().filter(recipient -> {
            Short preset = settings.presets().get(recipient.getId());

            if (preset == null) {
                // Run query on default preset if none are present
                return this.courseNotificationSettingPresetRegistryService.isPresetSettingEnabled(DEFAULT_PRESET_ID, notification.getClass(), filterFor);
            }
            else if (preset == CUSTOM_PRESET_ID) {
                var specification = settings.specifications().getOrDefault(recipient.getId(), List.of()).stream()
                        .filter(spec -> Objects.equals(spec.courseNotificationType(), notificationType)).findFirst();

                return specification.map(switch (filterFor) {
                    case WEBAPP -> UserCourseNotificationSettingSpecificationEntryDTO::webapp;
                    case PUSH -> UserCourseNotificationSettingSpecificationEntryDTO::push;
                    case EMAIL -> UserCourseNotificationSettingSpecificationEntryDTO::email;
                    // Custom presets created before a notification type was introduced have no specification row for it.
                    // Fall back to the default preset value instead of silently disabling delivery for those users.
                }).orElseGet(() -> this.courseNotificationSettingPresetRegistryService.isPresetSettingEnabled(DEFAULT_PRESET_ID, notification.getClass(), filterFor));
            }
            else {
                return this.courseNotificationSettingPresetRegistryService.isPresetSettingEnabled(preset, notification.getClass(), filterFor);
            }
        }).toList();
    }

    /**
     * Deletes all presets and specifications for a given user id.
     *
     * @param userId the user to delete for.
     */
    public void deleteAllForUser(long userId) {
        var presets = userCourseNotificationSettingPresetRepository.findAllByUserId(userId);
        var specifications = userCourseNotificationSettingSpecificationRepository.findAllByUserId(userId);

        userCourseNotificationSettingPresetRepository.deleteAll(presets);
        userCourseNotificationSettingSpecificationRepository.deleteAll(specifications);
    }
}
