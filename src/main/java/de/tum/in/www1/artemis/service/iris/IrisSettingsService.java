package de.tum.in.www1.artemis.service.iris;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.ws.rs.ForbiddenException;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettings;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSettingsRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * Service for managing {@link IrisSettings}.
 * It is used to manage and combine global, course and exercise specific settings.
 */
@Service
public class IrisSettingsService {

    private final CourseRepository courseRepository;

    private final ApplicationContext applicationContext;

    private final IrisSettingsRepository irisSettingsRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public IrisSettingsService(CourseRepository courseRepository, ApplicationContext applicationContext, IrisSettingsRepository irisSettingsRepository,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.courseRepository = courseRepository;
        this.applicationContext = applicationContext;
        this.irisSettingsRepository = irisSettingsRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Hooks into the {@link ApplicationReadyEvent} and creates the global IrisSettings object on startup.
     *
     * @param event Specifies when this method gets called and provides the event with all application data
     */
    @EventListener
    public void execute(ApplicationReadyEvent event) throws Exception {
        var settingsOptional = irisSettingsRepository.findAllGlobalSettings();
        if (settingsOptional.size() == 1) {
            return;
        }
        else if (settingsOptional.size() > 1) {
            var maxIdSettings = settingsOptional.stream().max(Comparator.comparingLong(IrisSettings::getId)).orElseThrow();
            settingsOptional.stream().filter(settings -> !Objects.equals(settings.getId(), maxIdSettings.getId())).forEach(irisSettingsRepository::delete);
            return;
        }

        if (event.getApplicationContext().getEnvironment().acceptsProfiles(Profiles.of("iris"))) {
            var settings = createDefaultIrisSettings(true);
            settings.setGlobal(true);
            settings.getIrisChatSettings().setEnabled(true);
            settings.getIrisChatSettings().setTemplate(new IrisTemplate(IrisConstants.DEFAULT_CHAT_TEMPLATE));
            settings.getIrisHestiaSettings().setEnabled(true);
            settings.getIrisHestiaSettings().setTemplate(new IrisTemplate(IrisConstants.DEFAULT_HESTIA_TEMPLATE));
            saveIrisSettings(settings);
        }
    }

    /**
     * Check if the Iris Hestia feature is enabled for a programming exercise, else throw a {@link ForbiddenException}.
     * See {@link #isIrisHestiaSessionEnabled(ProgrammingExercise)}
     *
     * @param programmingExercise the programming exercise for which to check the settings
     */
    public void checkIsIrisHestiaSessionEnabledElseThrow(ProgrammingExercise programmingExercise) {
        if (!isIrisHestiaSessionEnabled(programmingExercise)) {
            throw new AccessForbiddenException("Iris Hestia feature is not enabled for programming exercise " + programmingExercise.getId());
        }
    }

    /**
     * Check if the Iris chat feature is enabled for a programming exercise, else throw a {@link ForbiddenException}.
     * See {@link #isIrisChatSessionEnabled(ProgrammingExercise)}
     *
     * @param programmingExercise the programming exercise for which to check the settings
     */
    public void checkIsIrisChatSessionEnabledElseThrow(ProgrammingExercise programmingExercise) {
        if (!isIrisChatSessionEnabled(programmingExercise)) {
            throw new AccessForbiddenException("Iris Chat feature is not enabled for programming exercise " + programmingExercise.getId());
        }
    }

    /**
     * Check if the Iris Hestia feature is enabled for a programming exercise.
     * This is the case if they are enabled in the global and course settings and not disabled in the exercise settings.
     *
     * @param programmingExercise the programming exercise for which to check the settings
     * @return true if the Iris Hestia feature is enabled, false otherwise
     */
    public boolean isIrisHestiaSessionEnabled(ProgrammingExercise programmingExercise) {
        if (programmingExercise == null || programmingExercise.getIrisSettings() == null) {
            return false;
        }
        var settings = getCombinedIrisSettings(programmingExercise, true);
        return settings.getIrisHestiaSettings().isEnabled();
    }

    /**
     * Check if the Iris chat feature is enabled for a programming exercise.
     * This is the case if they are enabled in the global, course, and exercise settings.
     *
     * @param programmingExercise the programming exercise for which to check the settings
     * @return true if the Iris chat feature is enabled, false otherwise
     */
    public boolean isIrisChatSessionEnabled(ProgrammingExercise programmingExercise) {
        if (programmingExercise == null || programmingExercise.getIrisSettings() == null) {
            return false;
        }
        var settings = getCombinedIrisSettings(programmingExercise, true);
        return settings.getIrisChatSettings().isEnabled();
    }

    /**
     * Get the global Iris settings.
     *
     * @return the global Iris settings
     */
    public IrisSettings getGlobalSettings() {
        return irisSettingsRepository.findAllGlobalSettings().stream().max(Comparator.comparingLong(IrisSettings::getId)).orElseThrow();
    }

    /**
     * Get the Iris settings for a course. If no settings exist, a default settings object is created.
     * {@link IrisSettingsService#addDefaultIrisSettingsTo(Course)} for more details about the default settings.
     *
     * @param course the course for which to get the settings
     * @return the IrisSettings
     */
    public IrisSettings getIrisSettingsOrDefault(Course course) {
        if (course.getIrisSettings() == null || course.getIrisSettings().getId() == null) {
            return createDefaultIrisSettings(true);
        }
        return irisSettingsRepository.findById(course.getIrisSettings().getId()).orElse(createDefaultIrisSettings(true));
    }

    /**
     * Get the Iris settings for a programming exercise. If no settings exist, a default settings object is created.
     * {@link IrisSettingsService#addDefaultIrisSettingsTo(Course)} for more details about the default settings.
     *
     * @param exercise the programming exercise for which to get the settings
     * @return the IrisSettings
     */
    public IrisSettings getIrisSettingsOrDefault(ProgrammingExercise exercise) {
        if (exercise.getIrisSettings() == null || exercise.getIrisSettings().getId() == null) {
            return createDefaultIrisSettings(false);
        }
        return irisSettingsRepository.findById(exercise.getIrisSettings().getId()).orElse(createDefaultIrisSettings(true));
    }

    /**
     * Get the combined Iris settings for a course. Combines the global and course settings together.
     * The course settings override the global settings, except for the enabled flag, which is combined.
     *
     * @param course  the course for which to get the settings
     * @param reduced if true only the enabled flag is combined, otherwise all settings are combined
     * @return the combined IrisSettings
     */
    public IrisSettings getCombinedIrisSettings(Course course, boolean reduced) {
        var globalSettings = getGlobalSettings();
        var courseSettings = getIrisSettingsOrDefault(course);

        var combinedSettings = new IrisSettings();
        combinedSettings.setIrisChatSettings(combineSubSettings(globalSettings.getIrisChatSettings(), courseSettings.getIrisChatSettings(), false, reduced));
        combinedSettings.setIrisHestiaSettings(combineSubSettings(globalSettings.getIrisHestiaSettings(), courseSettings.getIrisHestiaSettings(), false, reduced));
        return combinedSettings;
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
        var courseSettings = getCombinedIrisSettings(programmingExercise.getCourseViaExerciseGroupOrCourseMember(), reduced);
        var exerciseSettings = getIrisSettingsOrDefault(programmingExercise);

        var combinedSettings = new IrisSettings();
        combinedSettings.setIrisChatSettings(combineSubSettings(courseSettings.getIrisChatSettings(), exerciseSettings.getIrisChatSettings(), false, reduced));
        combinedSettings.setIrisHestiaSettings(combineSubSettings(courseSettings.getIrisHestiaSettings(), exerciseSettings.getIrisHestiaSettings(), true, reduced));
        return combinedSettings;
    }

    /**
     * Combines the course and exercise sub-settings together.
     * The exercise settings override the course settings, but depending on the exerciseSettingsAreOptional parameter,
     * the combining strategy is different. If exerciseSettingsAreOptional is true, the course settings are used in full
     * if the exercise settings are null. Otherwise the exercise settings have to be present.
     *
     * @param subSettings1                 The course settings
     * @param subSettings2                 The exercise settings
     * @param secondSubSettingsAreOptional Whether the exercise settings are optional or not
     * @param reduced                      Whether only the enabled flag should be combined or all settings
     * @return The combined sub-settings
     */
    private IrisSubSettings combineSubSettings(IrisSubSettings subSettings1, IrisSubSettings subSettings2, boolean secondSubSettingsAreOptional, boolean reduced) {
        if (secondSubSettingsAreOptional && subSettings2 == null) {
            return subSettings1;
        }

        var combinedSettings = new IrisSubSettings();

        var enabled = subSettings2 != null && subSettings2.isEnabled() && subSettings1 != null && subSettings1.isEnabled()
                && applicationContext.getEnvironment().acceptsProfiles(Profiles.of("iris"));
        combinedSettings.setEnabled(enabled);

        if (!reduced) {
            String preferredModel = null;
            if (subSettings2 != null && subSettings2.getPreferredModel() != null) {
                preferredModel = subSettings2.getPreferredModel();
            }
            else if (subSettings1 != null && subSettings1.getPreferredModel() != null) {
                preferredModel = subSettings1.getPreferredModel();
            }
            combinedSettings.setPreferredModel(preferredModel);

            IrisTemplate template;
            if (subSettings2 != null && subSettings2.getTemplate() != null) {
                template = subSettings2.getTemplate();
            }
            else if (subSettings1 != null) {
                template = subSettings1.getTemplate();
            }
            else {
                template = null;
            }
            combinedSettings.setTemplate(template);
        }

        return combinedSettings;
    }

    /**
     * Adds the default Iris settings to a course if they are not present yet.
     *
     * @param course The course to add the default Iris settings to
     * @return The course with the default Iris settings
     */
    public Course addDefaultIrisSettingsTo(Course course) {
        if (course.getIrisSettings() != null) {
            return course;
        }
        course.setIrisSettings(createDefaultIrisSettings(true));
        return courseRepository.save(course);
    }

    /**
     * Adds the default Iris settings to a programming exercise if they are not present yet.
     *
     * @param programmingExercise The programming exercise to add the default Iris settings to
     * @return The programming exercise with the default Iris settings
     */
    public ProgrammingExercise addDefaultIrisSettingsTo(ProgrammingExercise programmingExercise) {
        if (programmingExercise.getIrisSettings() != null) {
            return programmingExercise;
        }
        programmingExercise.setIrisSettings(createDefaultIrisSettings(false));
        return programmingExerciseRepository.save(programmingExercise);
    }

    private IrisSettings createDefaultIrisSettings(boolean withOptionalSettings) {
        var irisSettings = new IrisSettings();
        irisSettings.setIrisChatSettings(createDefaultIrisSubSettings());
        irisSettings.setIrisHestiaSettings(withOptionalSettings ? createDefaultIrisSubSettings() : null);
        return irisSettings;
    }

    private IrisSubSettings createDefaultIrisSubSettings() {
        var subSettings = new IrisSubSettings();
        subSettings.setEnabled(false);
        subSettings.setPreferredModel(null);
        subSettings.setTemplate(null);
        return subSettings;
    }

    /**
     * Save the Iris settings. Should always be used over directly calling the repository.
     * Ensures that there is only one global Iris settings object.
     *
     * @param settings The Iris settings to save
     * @return The saved Iris settings
     */
    public IrisSettings saveIrisSettings(IrisSettings settings) {
        if (settings.isGlobal()) {
            var allGlobalSettings = irisSettingsRepository.findAllGlobalSettings();
            if (!allGlobalSettings.isEmpty() && !allGlobalSettings.stream().map(IrisSettings::getId).toList().equals(List.of(settings.getId()))) {
                throw new IllegalStateException("There can only be one global Iris settings object.");
            }
        }
        return irisSettingsRepository.save(settings);
    }

    /**
     * Save the global Iris settings.
     *
     * @param settings The Iris settings to save
     * @return The saved Iris settings
     */
    public IrisSettings saveGlobalIrisSettings(IrisSettings settings) {
        if (!settings.isGlobal()) {
            throw new BadRequestAlertException("The settings must be global", "IrisSettings", "notGlobal");
        }
        var globalSettings = getGlobalSettings();
        globalSettings.setIrisChatSettings(copyIrisSubSettings(globalSettings.getIrisChatSettings(), settings.getIrisChatSettings()));
        globalSettings.setIrisHestiaSettings(copyIrisSubSettings(globalSettings.getIrisHestiaSettings(), settings.getIrisHestiaSettings()));
        return irisSettingsRepository.save(globalSettings);
    }

    /**
     * Save the Iris settings for a course.
     *
     * @param course   The course for which to save the settings
     * @param settings The Iris settings to save
     * @return The saved Iris settings
     */
    public IrisSettings saveIrisSettings(Course course, IrisSettings settings) {
        var existingSettingsOptional = getIrisSettings(course);
        if (existingSettingsOptional.isPresent()) {
            var existingSettings = existingSettingsOptional.get();
            existingSettings.setIrisChatSettings(copyIrisSubSettings(existingSettings.getIrisChatSettings(), settings.getIrisChatSettings()));
            existingSettings.setIrisHestiaSettings(copyIrisSubSettings(existingSettings.getIrisHestiaSettings(), settings.getIrisHestiaSettings()));
            return saveIrisSettings(existingSettings);
        }
        else {
            settings.setId(null);
            course.setIrisSettings(saveIrisSettings(settings));
            var updatedCourse = courseRepository.save(course);
            return updatedCourse.getIrisSettings();
        }
    }

    /**
     * Save the Iris settings for a programming exercise.
     *
     * @param exercise the programming exercise for which to save the settings
     * @param settings the Iris settings to save
     * @return the saved Iris settings
     */
    public IrisSettings saveIrisSettings(ProgrammingExercise exercise, IrisSettings settings) {
        var existingSettingsOptional = getIrisSettings(exercise);
        if (existingSettingsOptional.isPresent()) {
            var existingSettings = existingSettingsOptional.get();
            existingSettings.setIrisChatSettings(copyIrisSubSettings(existingSettings.getIrisChatSettings(), settings.getIrisChatSettings()));
            existingSettings.setIrisHestiaSettings(copyIrisSubSettings(existingSettings.getIrisHestiaSettings(), settings.getIrisHestiaSettings()));
            return saveIrisSettings(existingSettings);
        }
        else {
            settings.setId(null);
            exercise.setIrisSettings(saveIrisSettings(settings));
            var updatedExercise = programmingExerciseRepository.save(exercise);
            return updatedExercise.getIrisSettings();
        }
    }

    private IrisSubSettings copyIrisSubSettings(IrisSubSettings target, IrisSubSettings source) {
        if (target == null || source == null) {
            return source;
        }
        target.setEnabled(source.isEnabled());
        target.setPreferredModel(source.getPreferredModel());
        if (!Objects.equals(source.getTemplate(), target.getTemplate())) {
            target.setTemplate(source.getTemplate());
        }
        return target;
    }

    /**
     * Get the Iris settings for a course. If no settings exist, an empty optional is returned.
     *
     * @param course the course for which to get the settings
     * @return the IrisSettings
     */
    private Optional<IrisSettings> getIrisSettings(Course course) {
        if (course.getIrisSettings() == null) {
            return Optional.empty();
        }
        return irisSettingsRepository.findById(course.getIrisSettings().getId());
    }

    /**
     * Get the Iris settings for a course. If no settings exist, an empty optional is returned.
     *
     * @param exercise the course for which to get the settings
     * @return the IrisSettings
     */
    private Optional<IrisSettings> getIrisSettings(ProgrammingExercise exercise) {
        if (exercise.getIrisSettings() == null) {
            return Optional.empty();
        }
        return irisSettingsRepository.findById(exercise.getIrisSettings().getId());
    }
}
