package de.tum.in.www1.artemis.service.iris.settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.domain.iris.settings.*;
import de.tum.in.www1.artemis.repository.iris.IrisSettingsRepository;
import de.tum.in.www1.artemis.service.dto.iris.IrisCombinedSettingsDTO;
import de.tum.in.www1.artemis.service.iris.IrisConstants;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenAlertException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * Service for managing {@link IrisSettings}.
 * This service is responsible for CRUD operations on {@link IrisSettings}.
 * It also provides methods for combining multiple {@link IrisSettings} and checking if a certain Iris feature is
 * enabled for an exercise.
 */
@Service
@Profile("iris")
public class IrisSettingsService {

    private final IrisSettingsRepository irisSettingsRepository;

    private final IrisSubSettingsService irisSubSettingsService;

    public IrisSettingsService(IrisSettingsRepository irisSettingsRepository, IrisSubSettingsService irisSubSettingsService) {
        this.irisSettingsRepository = irisSettingsRepository;
        this.irisSubSettingsService = irisSubSettingsService;
    }

    /**
     * Hooks into the {@link ApplicationReadyEvent} and creates or updates the global IrisSettings object on startup.
     *
     * @param event Specifies when this method gets called and provides the event with all application data
     */
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
        settings.setCurrentVersion(IrisConstants.GLOBAL_SETTINGS_VERSION);

        var chatSettings = new IrisChatSubSettings();
        chatSettings.setEnabled(false);
        chatSettings.setTemplate(new IrisTemplate(IrisConstants.DEFAULT_CHAT_TEMPLATE));
        settings.setIrisChatSettings(chatSettings);

        var hestiaSettings = new IrisHestiaSubSettings();
        hestiaSettings.setEnabled(false);
        hestiaSettings.setTemplate(new IrisTemplate(IrisConstants.DEFAULT_HESTIA_TEMPLATE));
        settings.setIrisHestiaSettings(hestiaSettings);

        updateIrisCodeEditorSettings(settings);

        irisSettingsRepository.save(settings);
    }

    /**
     * Auto updates the global IrisSettings object if the current version is outdated.
     *
     * @param settings The global IrisSettings object to update
     */
    private void autoUpdateGlobalSettings(IrisGlobalSettings settings) {
        if (settings.getCurrentVersion() < IrisConstants.GLOBAL_SETTINGS_VERSION) {
            if (settings.isEnableAutoUpdateChat() || settings.getIrisChatSettings() == null) {
                settings.getIrisChatSettings().setTemplate(new IrisTemplate(IrisConstants.DEFAULT_CHAT_TEMPLATE));
            }
            if (settings.isEnableAutoUpdateHestia() || settings.getIrisHestiaSettings() == null) {
                settings.getIrisHestiaSettings().setTemplate(new IrisTemplate(IrisConstants.DEFAULT_HESTIA_TEMPLATE));
            }
            if (settings.isEnableAutoUpdateCodeEditor() || settings.getIrisCodeEditorSettings() == null) {
                updateIrisCodeEditorSettings(settings);
            }
            settings.setCurrentVersion(IrisConstants.GLOBAL_SETTINGS_VERSION);
            saveIrisSettings(settings);
        }
    }

    private static void updateIrisCodeEditorSettings(IrisGlobalSettings settings) {
        var irisCodeEditorSettings = settings.getIrisCodeEditorSettings();
        if (irisCodeEditorSettings == null) {
            irisCodeEditorSettings = new IrisCodeEditorSubSettings();
            irisCodeEditorSettings.setEnabled(false);
        }
        irisCodeEditorSettings.setChatTemplate(new IrisTemplate(IrisConstants.DEFAULT_CODE_EDITOR_CHAT_TEMPLATE));
        irisCodeEditorSettings.setProblemStatementGenerationTemplate(new IrisTemplate(IrisConstants.DEFAULT_CODE_EDITOR_PROBLEM_STATEMENT_GENERATION_TEMPLATE));
        irisCodeEditorSettings.setTemplateRepoGenerationTemplate(new IrisTemplate(IrisConstants.DEFAULT_CODE_EDITOR_TEMPLATE_REPO_GENERATION_TEMPLATE));
        irisCodeEditorSettings.setSolutionRepoGenerationTemplate(new IrisTemplate(IrisConstants.DEFAULT_CODE_EDITOR_SOLUTION_REPO_GENERATION_TEMPLATE));
        irisCodeEditorSettings.setTestRepoGenerationTemplate(new IrisTemplate(IrisConstants.DEFAULT_CODE_EDITOR_TEST_REPO_GENERATION_TEMPLATE));
        settings.setIrisCodeEditorSettings(irisCodeEditorSettings);
    }

    public IrisGlobalSettings getGlobalSettings() {
        return irisSettingsRepository.findGlobalSettingsElseThrow();
    }

    /**
     * Save the Iris settings. Should always be used over directly calling the repository.
     * Automatically decides whether to save a new Iris settings object or update an existing one.
     *
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
     * Save the Iris settings. Should always be used over directly calling the repository.
     * Ensures that there is only one global Iris settings object.
     *
     * @param settings The Iris settings to save
     * @return The saved Iris settings
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

    @SuppressWarnings("unchecked")
    public <T extends IrisSettings> T updateIrisSettings(long existingSettingsId, T settingsUpdate) {
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

    private IrisGlobalSettings updateGlobalSettings(IrisGlobalSettings existingSettings, IrisGlobalSettings settingsUpdate) {
        existingSettings.setIrisChatSettings(irisSubSettingsService.update(existingSettings.getIrisChatSettings(), settingsUpdate.getIrisChatSettings(), null));
        existingSettings.setIrisHestiaSettings(irisSubSettingsService.update(existingSettings.getIrisHestiaSettings(), settingsUpdate.getIrisHestiaSettings(), null));
        existingSettings.setIrisCodeEditorSettings(irisSubSettingsService.update(existingSettings.getIrisCodeEditorSettings(), settingsUpdate.getIrisCodeEditorSettings(), null));
        return irisSettingsRepository.save(existingSettings);
    }

    private IrisCourseSettings updateCourseSettings(IrisCourseSettings existingSettings, IrisCourseSettings settingsUpdate) {
        var parentSettings = getCombinedIrisGlobalSettings();
        existingSettings.setIrisChatSettings(
                irisSubSettingsService.update(existingSettings.getIrisChatSettings(), settingsUpdate.getIrisChatSettings(), parentSettings.irisChatSettings()));
        existingSettings.setIrisHestiaSettings(
                irisSubSettingsService.update(existingSettings.getIrisHestiaSettings(), settingsUpdate.getIrisHestiaSettings(), parentSettings.irisHestiaSettings()));
        existingSettings.setIrisCodeEditorSettings(
                irisSubSettingsService.update(existingSettings.getIrisCodeEditorSettings(), settingsUpdate.getIrisCodeEditorSettings(), parentSettings.irisCodeEditorSettings()));
        return irisSettingsRepository.save(existingSettings);
    }

    private IrisExerciseSettings updateExerciseSettings(IrisExerciseSettings existingSettings, IrisExerciseSettings settingsUpdate) {
        var parentSettings = getCombinedIrisSettingsFor(existingSettings.getExercise().getCourseViaExerciseGroupOrCourseMember(), false);
        existingSettings.setIrisChatSettings(
                irisSubSettingsService.update(existingSettings.getIrisChatSettings(), settingsUpdate.getIrisChatSettings(), parentSettings.irisChatSettings()));
        return irisSettingsRepository.save(existingSettings);
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
        return switch (type) {
            case CHAT -> settings.irisChatSettings().isEnabled();
            case HESTIA -> settings.irisHestiaSettings().isEnabled();
            case CODE_EDITOR -> false; // FIXME: Implement this in another PR
        };
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

    public IrisCombinedSettingsDTO getCombinedIrisGlobalSettings() {
        var settingsList = new ArrayList<IrisSettings>();
        settingsList.add(getGlobalSettings());

        return new IrisCombinedSettingsDTO(irisSubSettingsService.combineChatSettings(settingsList, false), irisSubSettingsService.combineHestiaSettings(settingsList, false),
                irisSubSettingsService.combineCodeEditorSettings(settingsList, false));
    }

    public IrisCombinedSettingsDTO getCombinedIrisSettingsFor(Course course, boolean minimal) {
        var settingsList = new ArrayList<IrisSettings>();
        settingsList.add(getGlobalSettings());
        settingsList.add(irisSettingsRepository.findCourseSettings(course.getId()).orElse(null));

        return new IrisCombinedSettingsDTO(irisSubSettingsService.combineChatSettings(settingsList, minimal), irisSubSettingsService.combineHestiaSettings(settingsList, minimal),
                irisSubSettingsService.combineCodeEditorSettings(settingsList, minimal));
    }

    public IrisCombinedSettingsDTO getCombinedIrisSettingsFor(Exercise exercise, boolean minimal) {
        var settingsList = new ArrayList<IrisSettings>();
        settingsList.add(getGlobalSettings());
        settingsList.add(irisSettingsRepository.findCourseSettings(exercise.getCourseViaExerciseGroupOrCourseMember().getId()).orElse(null));
        settingsList.add(irisSettingsRepository.findExerciseSettings(exercise.getId()).orElse(null));

        return new IrisCombinedSettingsDTO(irisSubSettingsService.combineChatSettings(settingsList, minimal), irisSubSettingsService.combineHestiaSettings(settingsList, minimal),
                irisSubSettingsService.combineCodeEditorSettings(settingsList, minimal));
    }

    public IrisCourseSettings getDefaultSettingsFor(Course course) {
        var settings = new IrisCourseSettings();
        settings.setCourse(course);
        settings.setIrisChatSettings(new IrisChatSubSettings());
        settings.setIrisHestiaSettings(new IrisHestiaSubSettings());
        settings.setIrisCodeEditorSettings(new IrisCodeEditorSubSettings());
        return settings;
    }

    public IrisExerciseSettings getDefaultSettingsFor(Exercise exercise) {
        var settings = new IrisExerciseSettings();
        settings.setExercise(exercise);
        settings.setIrisChatSettings(new IrisChatSubSettings());
        return settings;
    }

    public IrisCourseSettings getRawIrisSettingsFor(Course course) {
        return irisSettingsRepository.findCourseSettings(course.getId()).orElse(getDefaultSettingsFor(course));
    }

    public IrisExerciseSettings getRawIrisSettingsFor(Exercise exercise) {
        return irisSettingsRepository.findExerciseSettings(exercise.getId()).orElse(getDefaultSettingsFor(exercise));
    }
}
