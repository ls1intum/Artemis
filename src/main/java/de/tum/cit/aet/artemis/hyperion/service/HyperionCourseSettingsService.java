package de.tum.cit.aet.artemis.hyperion.service;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettings;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettingsEntity;
import de.tum.cit.aet.artemis.hyperion.repository.HyperionCourseSettingsRepository;

/**
 * Reads and writes the Hyperion settings of a course.
 * <p>
 * A course without a settings row has the defaults; the row is created by the first update.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionCourseSettingsService {

    private final HyperionCourseSettingsRepository settingsRepository;

    public HyperionCourseSettingsService(HyperionCourseSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    /**
     * Returns the settings of a course, or the defaults when none were saved yet.
     *
     * @param courseId the course
     * @return the settings
     */
    public HyperionCourseSettings getSettings(long courseId) {
        return settingsRepository.findByCourseId(courseId).map(HyperionCourseSettingsEntity::getSettings).orElseGet(HyperionCourseSettings::defaultSettings);
    }

    /**
     * Replaces the settings of a course, creating the settings row when the course has none.
     *
     * @param courseId the course
     * @param settings the settings to store
     * @return the stored settings
     */
    public HyperionCourseSettings updateSettings(long courseId, HyperionCourseSettings settings) {
        HyperionCourseSettingsEntity entity = settingsRepository.findByCourseId(courseId).orElseGet(() -> {
            HyperionCourseSettingsEntity created = new HyperionCourseSettingsEntity();
            created.setCourseId(courseId);
            return created;
        });
        entity.setSettings(settings);
        return settingsRepository.save(entity).getSettings();
    }
}
