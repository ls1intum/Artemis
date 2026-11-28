package de.tum.cit.aet.artemis.hyperion.service;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettings;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettingsEntity;
import de.tum.cit.aet.artemis.hyperion.repository.HyperionCourseSettingsRepository;

/**
 * Reads and writes the Hyperion settings of a course.
 * <p>
 * Every course owns one settings row from its creation. A course whose row is missing reads as the defaults, and an update adds the
 * missing row before it writes.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionCourseSettingsService {

    private final HyperionCourseSettingsRepository settingsRepository;

    private final CourseRepository courseRepository;

    public HyperionCourseSettingsService(HyperionCourseSettingsRepository settingsRepository, CourseRepository courseRepository) {
        this.settingsRepository = settingsRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Returns the settings of a course, or the defaults when the course has no settings row.
     *
     * @param courseId the course
     * @return the settings
     */
    public HyperionCourseSettings getSettings(long courseId) {
        return settingsRepository.findByCourseId(courseId).map(HyperionCourseSettingsEntity::getSettings).orElseGet(HyperionCourseSettings::defaultSettings);
    }

    /**
     * Replaces the settings of a course in place. A course whose settings row is missing receives its default rows first.
     *
     * @param courseId the course
     * @param settings the settings to store
     * @return the stored settings
     */
    public HyperionCourseSettings updateSettings(long courseId, HyperionCourseSettings settings) {
        if (settingsRepository.updateSettings(courseId, settings) == 0) {
            courseRepository.ensureDefaultConfigurations(courseId);
            settingsRepository.updateSettings(courseId, settings);
        }
        return settings;
    }
}
