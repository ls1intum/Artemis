package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotification;
import de.tum.cit.aet.artemis.communication.domain.setting_presets.UserCourseNotificationSettingPreset;
import de.tum.cit.aet.artemis.communication.dto.UserCourseNotificationSettingPresetDTO;

/**
 * Registry service that provides options from the setting presets.
 */
@Profile(PROFILE_CORE)
@Service
public class CourseNotificationSettingPresetRegistryService {

    private static final Logger log = LoggerFactory.getLogger(CourseNotificationSettingPresetRegistryService.class);

    private final Map<Integer, UserCourseNotificationSettingPreset> presets = new HashMap<>();

    /**
     * Constructs a new CourseNotificationSettingPresetRegistry and automatically scans the application context for all
     * classes annotated with {@link CourseNotificationSettingPreset} in the {@code communication.domain.setting_presets}
     * directory. The registry then creates an instance for all preset types.
     */
    public CourseNotificationSettingPresetRegistryService() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(CourseNotificationSettingPreset.class));
        String basePackage = "de.tum.cit.aet.artemis.communication.domain.setting_presets";

        for (BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
            try {
                Class<?> classType = Class.forName(bd.getBeanClassName());

                if (UserCourseNotificationSettingPreset.class.isAssignableFrom(classType)) {
                    CourseNotificationSettingPreset annotation = classType.getAnnotation(CourseNotificationSettingPreset.class);
                    int typeId = annotation.value();

                    if (typeId == 0) {
                        throw new RuntimeException("The value 0 of the CourseNotificationSettingPreset decorator is "
                                + "reserved for 'Custom' specification and cannot be used. Please change to a different one.");
                    }

                    try {
                        UserCourseNotificationSettingPreset preset = (UserCourseNotificationSettingPreset) classType.getDeclaredConstructor().newInstance();
                        log.debug("Registering notification setting preset: {}, {}", typeId, classType.getSimpleName());
                        presets.put(typeId, preset);
                    }
                    catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                        log.error("Failed to instantiate preset class {}: {}", classType.getName(), e.getMessage());
                    }
                }
            }
            catch (ClassNotFoundException e) {
                log.error("Failed to load notification setting preset class", e);
            }
        }
    }

    /**
     * Returns a list of all registered presets in camelCase format.
     *
     * @return list of all registered presets in camelCase format.
     */
    public List<UserCourseNotificationSettingPresetDTO> getSettingPresetDTOs() {
        List<UserCourseNotificationSettingPresetDTO> presetDTOList = new ArrayList<>();

        presets.forEach((key, value) -> presetDTOList.add(
                new UserCourseNotificationSettingPresetDTO(value.getReadablePresetIdentifier(), getPresetId(value.getClass()).shortValue(), value.getPresetMapWithStringKeys())));

        return presetDTOList;
    }

    /**
     * Checks whether given preset identifier exists.
     *
     * @param settingPresetTypeId the identifier of the setting preset
     *
     * @return Returns {@code true} if preset exists and {@code false} otherwise.
     */
    public boolean isValidSettingPreset(Integer settingPresetTypeId) {
        return settingPresetTypeId == 0 || presets.containsKey(settingPresetTypeId);
    }

    /**
     * Finds whether a setting is enabled or disabled for preset beans annotated with {@link CourseNotificationSettingPreset}.
     *
     * @param typeId           the identifier of the setting preset
     * @param notificationType the type of the notification to look for
     * @param option           the option to look for (e.g. Webapp, Email, Push, ...)
     *
     * @return Returns {@code true} if notification of this type is enabled by the preset and {@code false} otherwise.
     */
    protected boolean isPresetSettingEnabled(int typeId, Class<? extends CourseNotification> notificationType, NotificationChannelOption option) {
        if (!presets.containsKey(typeId)) {
            return false;
        }

        return presets.get(typeId).isEnabled(notificationType, option);
    }

    /**
     * Retrieves the preset identifier (Integer key) for a given preset class.
     * Returns null if the class is not found in the registered presets.
     *
     * @param presetClass the class of the notification setting preset
     * @return the corresponding identifier for the preset, or null if not found
     */
    protected Integer getPresetId(Class<? extends UserCourseNotificationSettingPreset> presetClass) {
        for (Map.Entry<Integer, UserCourseNotificationSettingPreset> entry : presets.entrySet()) {
            if (entry.getValue().getClass().equals(presetClass)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Retreives preset by typeId specified by {@link CourseNotificationSettingPreset}.
     *
     * @param typeId the id of the preset
     * @return the corresponding preset, or null if not found
     */
    protected UserCourseNotificationSettingPreset getPresetById(Short typeId) {
        return presets.get((int) typeId);
    }
}
