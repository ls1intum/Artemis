package de.tum.in.www1.artemis.service.iris.settings;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_SCHEDULING;
import static de.tum.in.www1.artemis.domain.iris.settings.IrisSettingsType.COURSE;
import static de.tum.in.www1.artemis.domain.iris.settings.IrisSettingsType.EXERCISE;
import static de.tum.in.www1.artemis.domain.iris.settings.IrisSettingsType.GLOBAL;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.domain.iris.settings.IrisChatSubSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisCompetencyGenerationSubSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisCourseSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisExerciseSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisGlobalSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisHestiaSubSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.iris.IrisSettingsRepository;
import de.tum.in.www1.artemis.service.iris.IrisDefaultTemplateService;
import de.tum.in.www1.artemis.service.iris.dto.IrisCombinedSettingsDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenAlertException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * Service for managing {@link IrisSettings}.
 * This service is responsible for CRUD operations on {@link IrisSettings}.
 * It also provides methods for combining multiple {@link IrisSettings} and checking if a certain Iris feature is
 * enabled for an exercise.
 * See {@link IrisSubSettingsService} for more information on the handling of {@link IrisSubSettings}.
 */
@Service
@Profile("iris")
public class IrisSettingsService {

    private final IrisSettingsRepository irisSettingsRepository;

    private final IrisSubSettingsService irisSubSettingsService;

    private final IrisDefaultTemplateService irisDefaultTemplateService;

    public IrisSettingsService(IrisSettingsRepository irisSettingsRepository, IrisSubSettingsService irisSubSettingsService,
            IrisDefaultTemplateService irisDefaultTemplateService) {
        this.irisSettingsRepository = irisSettingsRepository;
        this.irisSubSettingsService = irisSubSettingsService;
        this.irisDefaultTemplateService = irisDefaultTemplateService;
    }

    private Optional<Integer> loadGlobalTemplateVersion() {
        return irisDefaultTemplateService.loadGlobalTemplateVersion();
    }

    private IrisTemplate loadDefaultChatTemplate() {
        return irisDefaultTemplateService.load("chat.hbs");
    }

    private IrisTemplate loadDefaultHestiaTemplate() {
        return irisDefaultTemplateService.load("hestia.hbs");
    }

    private IrisTemplate loadDefaultCompetencyGenerationTemplate() {
        return irisDefaultTemplateService.load("competency-generation.hbs");
    }

    /**
     * Hooks into the {@link ApplicationReadyEvent} and creates or updates the global IrisSettings object on startup.
     *
     * @param event Unused event param used to specify when the method should be executed
     */
    @Profile(PROFILE_SCHEDULING)
    @EventListener
    public void execute(ApplicationReadyEvent event) throws Exception {
        var allGlobalSettings = irisSettingsRepository.findAllGlobalSettings();
        if (allGlobalSettings.isEmpty()) {
            createInitialGlobalSettings();
            return;
        }
        if (allGlobalSettings.size() > 1) {
            var maxIdSettings = allGlobalSettings.stream().max(Comparator.comparingLong(IrisSettings::getId)).orElseThrow();
            allGlobalSettings.stream().filter(settings -> !Objects.equals(settings.getId(), maxIdSettings.getId())).forEach(irisSettingsRepository::delete);
            autoUpdateGlobalSettings(maxIdSettings);
        }
        else {
            autoUpdateGlobalSettings(allGlobalSettings.stream().findFirst().get());
        }
    }

    /**
     * Creates the initial global IrisSettings object.
     */
    private void createInitialGlobalSettings() {
        var settings = new IrisGlobalSettings();
        settings.setCurrentVersion(loadGlobalTemplateVersion().orElse(0));

        initializeIrisChatSettings(settings);
        initializeIrisHestiaSettings(settings);
        initializeIrisCompetencyGenerationSettings(settings);

        irisSettingsRepository.save(settings);
    }

    /**
     * Auto updates the global IrisSettings object if the current version is outdated.
     *
     * @param settings The global IrisSettings object to update
     */
    private void autoUpdateGlobalSettings(IrisGlobalSettings settings) {
        Optional<Integer> globalVersion = loadGlobalTemplateVersion();
        if (globalVersion.isEmpty() || settings.getCurrentVersion() < globalVersion.get()) {
            if (settings.isEnableAutoUpdateChat() || settings.getIrisChatSettings() == null) {
                initializeIrisChatSettings(settings);
            }
            if (settings.isEnableAutoUpdateHestia() || settings.getIrisHestiaSettings() == null) {
                initializeIrisHestiaSettings(settings);
            }
            if (settings.isEnableAutoUpdateCompetencyGeneration() || settings.getIrisCompetencyGenerationSettings() == null) {
                initializeIrisCompetencyGenerationSettings(settings);
            }

            globalVersion.ifPresent(settings::setCurrentVersion);
            saveIrisSettings(settings);
        }
    }

    private void initializeIrisChatSettings(IrisGlobalSettings settings) {
        var irisChatSettings = settings.getIrisChatSettings();
        if (irisChatSettings == null) {
            irisChatSettings = new IrisChatSubSettings();
            irisChatSettings.setEnabled(false);
        }
        irisChatSettings.setTemplate(loadDefaultChatTemplate());
        settings.setIrisChatSettings(irisChatSettings);
    }

    private void initializeIrisHestiaSettings(IrisGlobalSettings settings) {
        var irisHestiaSettings = settings.getIrisHestiaSettings();
        if (irisHestiaSettings == null) {
            irisHestiaSettings = new IrisHestiaSubSettings();
            irisHestiaSettings.setEnabled(false);
        }
        irisHestiaSettings.setTemplate(loadDefaultHestiaTemplate());
        settings.setIrisHestiaSettings(irisHestiaSettings);
    }

    private void initializeIrisCompetencyGenerationSettings(IrisGlobalSettings settings) {
        var irisCompetencyGenerationSettings = settings.getIrisCompetencyGenerationSettings();
        if (irisCompetencyGenerationSettings == null) {
            irisCompetencyGenerationSettings = new IrisCompetencyGenerationSubSettings();
            irisCompetencyGenerationSettings.setEnabled(false);
        }
        irisCompetencyGenerationSettings.setTemplate(loadDefaultCompetencyGenerationTemplate());
        settings.setIrisCompetencyGenerationSettings(irisCompetencyGenerationSettings);
    }

    public IrisGlobalSettings getGlobalSettings() {
        return irisSettingsRepository.findGlobalSettingsElseThrow();
    }

    /**
     * Save the Iris settings. Should always be used over directly calling the repository.
     * Automatically decides whether to save a new Iris settings object or update an existing one.
     *
     * @param <T>      The subtype of the IrisSettings object
     * @param settings The Iris settings to save
     * @return The saved Iris settings
     */
    public <T extends IrisSettings> T saveIrisSettings(T settings) {
        if (settings.getId() == null) {
            return saveNewIrisSettings(settings);
        }
        else {
            return updateIrisSettings(settings.getId(), settings);
        }
    }

    /**
     * Save a new IrisSettings object. Should always be used over directly calling the repository.
     * Ensures that the settings are valid and that no settings for the given object already exist.
     *
     * @param <T>      The subtype of the IrisSettings object
     * @param settings The IrisSettings to save
     * @return The saved IrisSettings
     */
    private <T extends IrisSettings> T saveNewIrisSettings(T settings) {
        if (settings instanceof IrisGlobalSettings) {
            throw new BadRequestAlertException("You can not create new global settings", "IrisSettings", "notGlobal");
        }
        if (!settings.isValid()) {
            throw new BadRequestAlertException("New Iris settings are not valid", "IrisSettings", "notValid");
        }
        if (settings instanceof IrisCourseSettings courseSettings && irisSettingsRepository.findCourseSettings(courseSettings.getCourse().getId()).isPresent()) {
            throw new ConflictException("Iris settings for this course already exist", "IrisSettings", "alreadyExists");
        }
        if (settings instanceof IrisExerciseSettings exerciseSettings && irisSettingsRepository.findExerciseSettings(exerciseSettings.getExercise().getId()).isPresent()) {
            throw new ConflictException("Iris settings for this exercise already exist", "IrisSettings", "alreadyExists");
        }
        return irisSettingsRepository.save(settings);
    }

    /**
     * Update an existing IrisSettings object. Should always be used over directly calling the repository.
     * Ensures that the settings are valid and that the existing settings ID matches the update ID.
     * Then updates the existing settings according to the type of the settings object.
     *
     * @param <T>                The subtype of the IrisSettings object
     * @param existingSettingsId The ID of the existing IrisSettings object
     * @param settingsUpdate     The Iris settings object to update
     * @return The updated IrisSettings
     */
    @SuppressWarnings("unchecked")
    private <T extends IrisSettings> T updateIrisSettings(long existingSettingsId, T settingsUpdate) {
        if (!Objects.equals(existingSettingsId, settingsUpdate.getId())) {
            throw new ConflictException("Existing Iris settings ID does not match update ID", "IrisSettings", "idMismatch");
        }
        if (!settingsUpdate.isValid()) {
            throw new BadRequestAlertException("Updated Iris settings are not valid", "IrisSettings", "notValid");
        }

        var existingSettings = irisSettingsRepository.findByIdElseThrow(existingSettingsId);

        if (existingSettings instanceof IrisGlobalSettings globalSettings && settingsUpdate instanceof IrisGlobalSettings globalSettingsUpdate) {
            return (T) updateGlobalSettings(globalSettings, globalSettingsUpdate);
        }
        else if (existingSettings instanceof IrisCourseSettings courseSettings && settingsUpdate instanceof IrisCourseSettings courseSettingsUpdate) {
            return (T) updateCourseSettings(courseSettings, courseSettingsUpdate);
        }
        else if (existingSettings instanceof IrisExerciseSettings exerciseSettings && settingsUpdate instanceof IrisExerciseSettings exerciseSettingsUpdate) {
            return (T) updateExerciseSettings(exerciseSettings, exerciseSettingsUpdate);
        }
        else {
            throw new BadRequestAlertException("Unknown Iris settings type", "IrisSettings", "unknownType");
        }
    }

    /**
     * Helper method to update global Iris settings.
     *
     * @param existingSettings The existing global Iris settings
     * @param settingsUpdate   The global Iris settings to update
     * @return The updated global Iris settings
     */
    private IrisGlobalSettings updateGlobalSettings(IrisGlobalSettings existingSettings, IrisGlobalSettings settingsUpdate) {
        existingSettings.setCurrentVersion(settingsUpdate.getCurrentVersion());

        existingSettings.setEnableAutoUpdateChat(settingsUpdate.isEnableAutoUpdateChat());
        existingSettings.setEnableAutoUpdateHestia(settingsUpdate.isEnableAutoUpdateHestia());
        existingSettings.setEnableAutoUpdateCompetencyGeneration(settingsUpdate.isEnableAutoUpdateCompetencyGeneration());

        existingSettings.setIrisChatSettings(irisSubSettingsService.update(existingSettings.getIrisChatSettings(), settingsUpdate.getIrisChatSettings(), null, GLOBAL));
        existingSettings.setIrisHestiaSettings(irisSubSettingsService.update(existingSettings.getIrisHestiaSettings(), settingsUpdate.getIrisHestiaSettings(), null, GLOBAL));
        existingSettings.setIrisCompetencyGenerationSettings(
                irisSubSettingsService.update(existingSettings.getIrisCompetencyGenerationSettings(), settingsUpdate.getIrisCompetencyGenerationSettings(), null, GLOBAL));

        return irisSettingsRepository.save(existingSettings);
    }

    /**
     * Helper method to update course Iris settings.
     *
     * @param existingSettings The existing course Iris settings
     * @param settingsUpdate   The course Iris settings to update
     * @return The updated course Iris settings
     */
    private IrisCourseSettings updateCourseSettings(IrisCourseSettings existingSettings, IrisCourseSettings settingsUpdate) {
        var parentSettings = getCombinedIrisGlobalSettings();
        existingSettings.setIrisChatSettings(
                irisSubSettingsService.update(existingSettings.getIrisChatSettings(), settingsUpdate.getIrisChatSettings(), parentSettings.irisChatSettings(), COURSE));
        existingSettings.setIrisHestiaSettings(
                irisSubSettingsService.update(existingSettings.getIrisHestiaSettings(), settingsUpdate.getIrisHestiaSettings(), parentSettings.irisHestiaSettings(), COURSE));
        existingSettings.setIrisCompetencyGenerationSettings(irisSubSettingsService.update(existingSettings.getIrisCompetencyGenerationSettings(),
                settingsUpdate.getIrisCompetencyGenerationSettings(), parentSettings.irisCompetencyGenerationSettings(), COURSE));

        return irisSettingsRepository.save(existingSettings);
    }

    /**
     * Helper method to update exercise Iris settings.
     *
     * @param existingSettings The existing exercise Iris settings
     * @param settingsUpdate   The exercise Iris settings to update
     * @return The updated exercise Iris settings
     */
    private IrisExerciseSettings updateExerciseSettings(IrisExerciseSettings existingSettings, IrisExerciseSettings settingsUpdate) {
        var parentSettings = getCombinedIrisSettingsFor(existingSettings.getExercise().getCourseViaExerciseGroupOrCourseMember(), false);
        existingSettings.setIrisChatSettings(
                irisSubSettingsService.update(existingSettings.getIrisChatSettings(), settingsUpdate.getIrisChatSettings(), parentSettings.irisChatSettings(), EXERCISE));
        return irisSettingsRepository.save(existingSettings);
    }

    /**
     * Checks whether an Iris feature is enabled for a course.
     * Throws an exception if the feature is disabled.
     *
     * @param type   The Iris feature to check
     * @param course The course to check
     */
    public void isEnabledForElseThrow(IrisSubSettingsType type, Course course) {
        if (!isEnabledFor(type, course)) {
            throw new AccessForbiddenAlertException("The Iris " + type.name() + " feature is disabled for this course.", "Iris", "iris." + type.name().toLowerCase() + "Disabled");
        }
    }

    /**
     * Checks whether an Iris feature is enabled for a course.
     *
     * @param type   The Iris feature to check
     * @param course The course to check
     * @return Whether the Iris feature is enabled for the course
     */
    public boolean isEnabledFor(IrisSubSettingsType type, Course course) {
        var settings = getCombinedIrisSettingsFor(course, true);
        return isFeatureEnabledInSettings(settings, type);
    }

    /**
     * Checks whether an Iris feature is enabled for an exercise.
     *
     * @param type     The Iris feature to check
     * @param exercise The exercise to check
     * @return Whether the Iris feature is enabled for the exercise
     */
    public boolean isEnabledFor(IrisSubSettingsType type, Exercise exercise) {
        var settings = getCombinedIrisSettingsFor(exercise, true);
        return isFeatureEnabledInSettings(settings, type);
    }

    /**
     * Checks whether an Iris feature is enabled for an exercise.
     * Throws an exception if the feature is disabled.
     *
     * @param type     The Iris feature to check
     * @param exercise The exercise to check
     */
    public void isEnabledForElseThrow(IrisSubSettingsType type, Exercise exercise) {
        if (!isEnabledFor(type, exercise)) {
            throw new AccessForbiddenAlertException("The Iris " + type.name() + " feature is disabled for this exercise.", "Iris",
                    "iris." + type.name().toLowerCase() + "Disabled");
        }
    }

    /**
     * Get the global Iris settings as an {@link IrisCombinedSettingsDTO}.
     *
     * @return The (combined) global Iris settings
     */
    public IrisCombinedSettingsDTO getCombinedIrisGlobalSettings() {
        var settingsList = new ArrayList<IrisSettings>();
        settingsList.add(getGlobalSettings());

        return new IrisCombinedSettingsDTO(irisSubSettingsService.combineChatSettings(settingsList, false), irisSubSettingsService.combineHestiaSettings(settingsList, false),
                irisSubSettingsService.combineCompetencyGenerationSettings(settingsList, false));
    }

    /**
     * Get the combined Iris settings for a course as an {@link IrisCombinedSettingsDTO}.
     * Combines the global Iris settings with the course Iris settings.
     * If minimal is true, only certain attributes are returned. The minimal version can safely be passed to the students.
     * See also {@link IrisSubSettingsService} for how the combining works in detail
     *
     * @param course  The course to get the Iris settings for
     * @param minimal Whether to return the minimal version of the settings
     * @return The combined Iris settings for the course
     */
    public IrisCombinedSettingsDTO getCombinedIrisSettingsFor(Course course, boolean minimal) {
        var settingsList = new ArrayList<IrisSettings>();
        settingsList.add(getGlobalSettings());
        settingsList.add(irisSettingsRepository.findCourseSettings(course.getId()).orElse(null));

        return new IrisCombinedSettingsDTO(irisSubSettingsService.combineChatSettings(settingsList, minimal), irisSubSettingsService.combineHestiaSettings(settingsList, minimal),
                irisSubSettingsService.combineCompetencyGenerationSettings(settingsList, minimal));
    }

    /**
     * Get the combined Iris settings for an exercise as an {@link IrisCombinedSettingsDTO}.
     * Combines the global Iris settings with the course Iris settings and the exercise Iris settings.
     * If minimal is true, only certain attributes are returned. The minimal version can safely be passed to the students.
     * See also {@link IrisSubSettingsService} for how the combining works in detail
     *
     * @param exercise The exercise to get the Iris settings for
     * @param minimal  Whether to return the minimal version of the settings
     * @return The combined Iris settings for the exercise
     */
    public IrisCombinedSettingsDTO getCombinedIrisSettingsFor(Exercise exercise, boolean minimal) {
        var settingsList = new ArrayList<IrisSettings>();
        settingsList.add(getGlobalSettings());
        settingsList.add(getRawIrisSettingsFor(exercise.getCourseViaExerciseGroupOrCourseMember()));
        settingsList.add(getRawIrisSettingsFor(exercise));

        return new IrisCombinedSettingsDTO(irisSubSettingsService.combineChatSettings(settingsList, minimal), irisSubSettingsService.combineHestiaSettings(settingsList, minimal),
                irisSubSettingsService.combineCompetencyGenerationSettings(settingsList, minimal));
    }

    /**
     * Get the default Iris settings for a course.
     * The default settings are used if no Iris settings for the course exist.
     *
     * @param course The course to get the default Iris settings for
     * @return The default Iris settings for the course
     */
    public IrisCourseSettings getDefaultSettingsFor(Course course) {
        var settings = new IrisCourseSettings();
        settings.setCourse(course);
        settings.setIrisChatSettings(new IrisChatSubSettings());
        settings.setIrisHestiaSettings(new IrisHestiaSubSettings());
        settings.setIrisCompetencyGenerationSettings(new IrisCompetencyGenerationSubSettings());
        return settings;
    }

    /**
     * Get the default Iris settings for an exercise.
     * The default settings are used if no Iris settings for the exercise exist.
     *
     * @param exercise The exercise to get the default Iris settings for
     * @return The default Iris settings for the exercise
     */
    public IrisExerciseSettings getDefaultSettingsFor(Exercise exercise) {
        var settings = new IrisExerciseSettings();
        settings.setExercise(exercise);
        settings.setIrisChatSettings(new IrisChatSubSettings());
        return settings;
    }

    /**
     * Get the raw (uncombined) Iris settings for a course.
     * If no Iris settings for the course exist, the default settings are returned.
     *
     * @param course The course to get the Iris settings for
     * @return The raw Iris settings for the course
     */
    public IrisCourseSettings getRawIrisSettingsFor(Course course) {
        return irisSettingsRepository.findCourseSettings(course.getId()).orElse(getDefaultSettingsFor(course));
    }

    /**
     * Get the raw (uncombined) Iris settings for an exercise.
     * If no Iris settings for the exercise exist, the default settings are returned.
     *
     * @param exercise The exercise to get the Iris settings for
     * @return The raw Iris settings for the exercise
     */
    public IrisExerciseSettings getRawIrisSettingsFor(Exercise exercise) {
        return irisSettingsRepository.findExerciseSettings(exercise.getId()).orElse(getDefaultSettingsFor(exercise));
    }

    /**
     * Delete the Iris settings for a course.
     * If no Iris settings for the course exist, nothing happens.
     *
     * @param course The course to delete the Iris settings for
     */
    public void deleteSettingsFor(Course course) {
        var irisCourseSettingsOptional = irisSettingsRepository.findCourseSettings(course.getId());
        irisCourseSettingsOptional.ifPresent(irisSettingsRepository::delete);
    }

    /**
     * Delete the Iris settings for an exercise.
     * If no Iris settings for the exercise exist, nothing happens.
     *
     * @param exercise The course to delete the Iris settings for
     */
    public void deleteSettingsFor(Exercise exercise) {
        var irisExerciseSettingsOptional = irisSettingsRepository.findExerciseSettings(exercise.getId());
        irisExerciseSettingsOptional.ifPresent(irisSettingsRepository::delete);
    }

    /**
     * Checks if whether an Iris feature is enabled in the given settings
     *
     * @param settings the settings
     * @param type     the type of the feature
     * @return Whether the settings type is enabled
     */
    private boolean isFeatureEnabledInSettings(IrisCombinedSettingsDTO settings, IrisSubSettingsType type) {
        return switch (type) {
            case CHAT -> settings.irisChatSettings().enabled();
            case HESTIA -> settings.irisHestiaSettings().enabled();
            case COMPETENCY_GENERATION -> settings.irisCompetencyGenerationSettings().enabled();
        };
    }
}
