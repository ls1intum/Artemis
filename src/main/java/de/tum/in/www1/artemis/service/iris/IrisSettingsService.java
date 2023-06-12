package de.tum.in.www1.artemis.service.iris;

import javax.ws.rs.ForbiddenException;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettings;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSettingsRepository;

@Service
public class IrisSettingsService {

    private final CourseRepository courseRepository;

    private final IrisSettingsRepository irisSettingsRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public IrisSettingsService(CourseRepository courseRepository, IrisSettingsRepository irisSettingsRepository, ProgrammingExerciseRepository programmingExerciseRepository) {
        this.courseRepository = courseRepository;
        this.irisSettingsRepository = irisSettingsRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    public void checkIsIrisHestiaSessionEnabledElseThrow(ProgrammingExercise programmingExercise) {
        if (!isIrisHestiaSessionEnabled(programmingExercise)) {
            throw new ForbiddenException("Iris Hestia feature is not enabled for programming exercise " + programmingExercise.getId());
        }
    }

    public void checkIsIrisChatSessionEnabledElseThrow(ProgrammingExercise programmingExercise) {
        if (!isIrisChatSessionEnabled(programmingExercise)) {
            throw new ForbiddenException("Iris Chat feature is not enabled for programming exercise " + programmingExercise.getId());
        }
    }

    public boolean isIrisHestiaSessionEnabled(ProgrammingExercise programmingExercise) {
        if (programmingExercise == null || programmingExercise.getIrisSettings() == null) {
            return false;
        }
        var settings = getCombinedIrisSettings(programmingExercise, true);
        return settings.getIrisHestiaSettings().isEnabled();
    }

    public boolean isIrisChatSessionEnabled(ProgrammingExercise programmingExercise) {
        if (programmingExercise == null || programmingExercise.getIrisSettings() == null) {
            return false;
        }
        var settings = getCombinedIrisSettings(programmingExercise, true);
        return settings.getIrisChatSettings().isEnabled();
    }

    public IrisSettings getIrisSettings(Course course) {
        return irisSettingsRepository.findByCourseId(course.getId()).orElse(createDefaultIrisSettings());
    }

    /**
     * Get the combined Iris settings for a programming exercise. Combines the course and exercise settings together.
     * The exercise settings override the course settings, but depending on the sub settings type, the combining strategy is different.
     * ChatSettings: exercise settings are mandatory for the chat feature to be enabled
     * HestiaSettings: exercise settings are optional for the hestia feature to be enabled
     *
     * @param programmingExercise the programming exercise for which to get the settings
     * @param reduced             if true only the enabled flag is combined, otherwise all settings are combined
     * @return the combined IrisSettings
     */
    public IrisSettings getCombinedIrisSettings(ProgrammingExercise programmingExercise, boolean reduced) {
        var courseSettings = getIrisSettings(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        var exerciseSettings = irisSettingsRepository.findByProgrammingExerciseId(programmingExercise.getId());

        var combinedSettings = new IrisSettings();
        combinedSettings.setIrisChatSettings(
                combineSubSettings(courseSettings.getIrisChatSettings(), exerciseSettings.map(IrisSettings::getIrisChatSettings).orElse(null), false, reduced));
        combinedSettings.setIrisHestiaSettings(
                combineSubSettings(courseSettings.getIrisHestiaSettings(), exerciseSettings.map(IrisSettings::getIrisHestiaSettings).orElse(null), true, reduced));
        return combinedSettings;
    }

    private IrisSubSettings combineSubSettings(IrisSubSettings courseSettings, IrisSubSettings exerciseSettings, boolean exerciseSettingsAreOptional, boolean reduced) {
        if (exerciseSettingsAreOptional && exerciseSettings == null) {
            return courseSettings;
        }

        var combinedSettings = new IrisSubSettings();

        var enabled = exerciseSettings != null && exerciseSettings.isEnabled() && courseSettings != null && courseSettings.isEnabled();
        combinedSettings.setEnabled(enabled);

        if (!reduced) {
            var preferredModel = exerciseSettings != null && exerciseSettings.getPreferredModel() != null ? exerciseSettings.getPreferredModel()
                    : courseSettings != null ? courseSettings.getPreferredModel() : null;
            combinedSettings.setPreferredModel(preferredModel);

            var externalTemplateId = exerciseSettings != null && exerciseSettings.getExternalTemplateId() != null ? exerciseSettings.getExternalTemplateId()
                    : courseSettings != null ? courseSettings.getExternalTemplateId() : null;
            combinedSettings.setExternalTemplateId(externalTemplateId);
        }

        return combinedSettings;
    }

    public Course addDefaultIrisSettingsTo(Course course) {
        if (course.getIrisSettings() != null) {
            return course;
        }
        course.setIrisSettings(createDefaultIrisSettings());
        return courseRepository.save(course);
    }

    public ProgrammingExercise addDefaultIrisSettingsTo(ProgrammingExercise programmingExercise) {
        if (programmingExercise.getIrisSettings() != null) {
            return programmingExercise;
        }
        programmingExercise.setIrisSettings(createDefaultIrisSettings());
        return programmingExerciseRepository.save(programmingExercise);
    }

    private IrisSettings createDefaultIrisSettings() {
        var irisSettings = new IrisSettings();
        irisSettings.setIrisChatSettings(createDefaultIrisSubSettings());
        irisSettings.setIrisHestiaSettings(createDefaultIrisSubSettings());
        return irisSettings;
    }

    private IrisSubSettings createDefaultIrisSubSettings() {
        var subSettings = new IrisSubSettings();
        subSettings.setEnabled(false);
        subSettings.setPreferredModel(null);
        subSettings.setExternalTemplateId(null);
        return subSettings;
    }
}
