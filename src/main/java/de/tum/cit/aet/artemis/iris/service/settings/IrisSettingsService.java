package de.tum.cit.aet.artemis.iris.service.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;
import static de.tum.cit.aet.artemis.iris.domain.settings.IrisSettingsType.COURSE;
import static de.tum.cit.aet.artemis.iris.domain.settings.IrisSettingsType.EXERCISE;
import static de.tum.cit.aet.artemis.iris.domain.settings.IrisSettingsType.GLOBAL;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCompetencyGenerationSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisExerciseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisGlobalSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisLectureIngestionSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisTextExerciseChatSubSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedSettingsDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

/**
 * Service for managing {@link IrisSettings}.
 * This service is responsible for CRUD operations on {@link IrisSettings}.
 * It also provides methods for combining multiple {@link IrisSettings} and checking if a certain Iris feature is
 * enabled for an exercise.
 * See {@link IrisSubSettingsService} for more information on the handling of {@link IrisSubSettings}.
 */
@Service
@Profile(PROFILE_IRIS)
public class IrisSettingsService {

    private final IrisSettingsRepository irisSettingsRepository;

    private final IrisSubSettingsService irisSubSettingsService;

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ObjectMapper objectMapper;

    private final TextExerciseRepository textExerciseRepository;

    public IrisSettingsService(IrisSettingsRepository irisSettingsRepository, IrisSubSettingsService irisSubSettingsService, AuthorizationCheckService authCheckService,
            ProgrammingExerciseRepository programmingExerciseRepository, ObjectMapper objectMapper, TextExerciseRepository textExerciseRepository) {
        this.irisSettingsRepository = irisSettingsRepository;
        this.irisSubSettingsService = irisSubSettingsService;
        this.authCheckService = authCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.objectMapper = objectMapper;
        this.textExerciseRepository = textExerciseRepository;
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
        }
    }

    /**
     * Creates the initial global IrisSettings object.
     */
    private void createInitialGlobalSettings() {
        var settings = new IrisGlobalSettings();

        initializeIrisChatSettings(settings);
        initializeIrisTextExerciseChatSettings(settings);
        initializeIrisLectureIngestionSettings(settings);
        initializeIrisCompetencyGenerationSettings(settings);

        irisSettingsRepository.save(settings);
    }

    private static <T extends IrisSubSettings> T initializeSettings(T settings, Supplier<T> constructor) {
        if (settings == null) {
            settings = constructor.get();
            settings.setEnabled(false);
            settings.setAllowedVariants(new TreeSet<>(Set.of("default")));
            settings.setSelectedVariant("default");
        }
        return settings;
    }

    private void initializeIrisChatSettings(IrisGlobalSettings settings) {
        var irisChatSettings = settings.getIrisChatSettings();
        irisChatSettings = initializeSettings(irisChatSettings, IrisChatSubSettings::new);
        settings.setIrisChatSettings(irisChatSettings);
    }

    private void initializeIrisTextExerciseChatSettings(IrisGlobalSettings settings) {
        var irisChatSettings = settings.getIrisTextExerciseChatSettings();
        irisChatSettings = initializeSettings(irisChatSettings, IrisTextExerciseChatSubSettings::new);
        settings.setIrisTextExerciseChatSettings(irisChatSettings);
    }

    private void initializeIrisLectureIngestionSettings(IrisGlobalSettings settings) {
        var irisLectureIngestionSettings = settings.getIrisLectureIngestionSettings();
        irisLectureIngestionSettings = initializeSettings(irisLectureIngestionSettings, IrisLectureIngestionSubSettings::new);
        settings.setIrisLectureIngestionSettings(irisLectureIngestionSettings);
    }

    private void initializeIrisCompetencyGenerationSettings(IrisGlobalSettings settings) {
        var irisCompetencyGenerationSettings = settings.getIrisCompetencyGenerationSettings();
        irisCompetencyGenerationSettings = initializeSettings(irisCompetencyGenerationSettings, IrisCompetencyGenerationSubSettings::new);
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
        // @formatter:off
        existingSettings.setIrisLectureIngestionSettings(irisSubSettingsService.update(
                existingSettings.getIrisLectureIngestionSettings(),
                settingsUpdate.getIrisLectureIngestionSettings(),
                null,
                GLOBAL
        ));
        existingSettings.setIrisTextExerciseChatSettings(irisSubSettingsService.update(
                existingSettings.getIrisTextExerciseChatSettings(),
                settingsUpdate.getIrisTextExerciseChatSettings(),
                null,
                GLOBAL
        ));
        existingSettings.setIrisChatSettings(irisSubSettingsService.update(
                existingSettings.getIrisChatSettings(),
                settingsUpdate.getIrisChatSettings(),
                null,
                GLOBAL
        ));
        existingSettings.setIrisCompetencyGenerationSettings(irisSubSettingsService.update(
                existingSettings.getIrisCompetencyGenerationSettings(),
                settingsUpdate.getIrisCompetencyGenerationSettings(),
                null,
                GLOBAL
        ));
        // @formatter:on

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
        var oldEnabledForCategoriesExerciseChat = existingSettings.getIrisChatSettings() == null ? new TreeSet<String>()
                : existingSettings.getIrisChatSettings().getEnabledForCategories();
        var oldEnabledForCategoriesTextExerciseChat = existingSettings.getIrisTextExerciseChatSettings() == null ? new TreeSet<String>()
                : existingSettings.getIrisTextExerciseChatSettings().getEnabledForCategories();

        var parentSettings = getCombinedIrisGlobalSettings();
        // @formatter:off
        existingSettings.setIrisChatSettings(irisSubSettingsService.update(
                existingSettings.getIrisChatSettings(),
                settingsUpdate.getIrisChatSettings(),
                parentSettings.irisChatSettings(),
                COURSE
        ));
        existingSettings.setIrisTextExerciseChatSettings(irisSubSettingsService.update(
                existingSettings.getIrisTextExerciseChatSettings(),
                settingsUpdate.getIrisTextExerciseChatSettings(),
                parentSettings.irisTextExerciseChatSettings(),
                COURSE
        ));
        existingSettings.setIrisLectureIngestionSettings(irisSubSettingsService.update(
                existingSettings.getIrisLectureIngestionSettings(),
                settingsUpdate.getIrisLectureIngestionSettings(),
                parentSettings.irisLectureIngestionSettings(),
                COURSE
        ));
        existingSettings.setIrisCompetencyGenerationSettings(irisSubSettingsService.update(
                existingSettings.getIrisCompetencyGenerationSettings(),
                settingsUpdate.getIrisCompetencyGenerationSettings(),
                parentSettings.irisCompetencyGenerationSettings(),
                COURSE
        ));
        // @formatter:on

        // Automatically update the exercise settings when the enabledForCategories is changed
        var newEnabledForCategoriesExerciseChat = existingSettings.getIrisChatSettings() == null ? new TreeSet<String>()
                : existingSettings.getIrisChatSettings().getEnabledForCategories();
        if (!oldEnabledForCategoriesExerciseChat.equals(newEnabledForCategoriesExerciseChat)) {
            programmingExerciseRepository.findAllWithCategoriesByCourseId(existingSettings.getCourse().getId())
                    .forEach(exercise -> setEnabledForExerciseByCategories(exercise, oldEnabledForCategoriesExerciseChat, newEnabledForCategoriesExerciseChat));
        }

        var newEnabledForCategoriesTextExerciseChat = existingSettings.getIrisTextExerciseChatSettings() == null ? new TreeSet<String>()
                : existingSettings.getIrisTextExerciseChatSettings().getEnabledForCategories();
        if (!Objects.equals(oldEnabledForCategoriesTextExerciseChat, newEnabledForCategoriesTextExerciseChat)) {
            textExerciseRepository.findAllWithCategoriesByCourseId(existingSettings.getCourse().getId())
                    .forEach(exercise -> setEnabledForExerciseByCategories(exercise, oldEnabledForCategoriesTextExerciseChat, newEnabledForCategoriesTextExerciseChat));
        }

        return irisSettingsRepository.save(existingSettings);
    }

    /**
     * Set the enabled status for an exercise based on it's categories.
     * Compares the old and new enabled categories, reads the exercise categories,
     * and updates the Iris chat settings accordingly if the new enabled categories match any of the exercise categories.
     * This method is used when the enabled categories of the course settings are updated.
     *
     * @param exercise                The exercise to update the enabled status for
     * @param oldEnabledForCategories The old enabled categories
     * @param newEnabledForCategories The new enabled categories
     */
    public void setEnabledForExerciseByCategories(Exercise exercise, SortedSet<String> oldEnabledForCategories, SortedSet<String> newEnabledForCategories) {
        var removedCategories = new TreeSet<>(oldEnabledForCategories);
        removedCategories.removeAll(newEnabledForCategories);
        var categories = getCategoryNames(exercise.getCategories());

        if (categories.stream().anyMatch(newEnabledForCategories::contains)) {
            setExerciseSettingsEnabled(exercise, true);
        }
        else if (categories.stream().anyMatch(removedCategories::contains)) {
            setExerciseSettingsEnabled(exercise, false);
        }
    }

    /**
     * Set the enabled status for an exercise based on its categories.
     * Reads the exercise categories and updates the Iris chat settings accordingly if the enabled categories match any of the exercise categories.
     * This method is used when the categories of an exercise are updated.
     *
     * @param exercise              The exercise to update the enabled status for
     * @param oldExerciseCategories The old exercise categories
     */
    public void setEnabledForExerciseByCategories(Exercise exercise, Set<String> oldExerciseCategories) {
        var oldCategories = getCategoryNames(oldExerciseCategories);
        var newCategories = getCategoryNames(exercise.getCategories());
        if (oldCategories.isEmpty() && newCategories.isEmpty()) {
            return;
        }

        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var courseSettings = getRawIrisSettingsFor(course);

        Set<String> enabledForCategories;
        if (exercise instanceof ProgrammingExercise) {
            enabledForCategories = courseSettings.getIrisChatSettings().getEnabledForCategories();
        }
        else if (exercise instanceof TextExercise) {
            enabledForCategories = courseSettings.getIrisTextExerciseChatSettings().getEnabledForCategories();
        }
        else {
            return;
        }
        if (enabledForCategories == null) {
            return;
        }

        if (newCategories.stream().anyMatch(enabledForCategories::contains)) {
            setExerciseSettingsEnabled(exercise, true);
        }
        else if (oldCategories.stream().anyMatch(enabledForCategories::contains)) {
            setExerciseSettingsEnabled(exercise, false);
        }
    }

    /**
     * Helper method to set the enabled status for an exercise's Iris settings.
     * Currently able to handle {@link ProgrammingExercise} and {@link TextExercise} settings.
     *
     * @param exercise The exercise to update the enabled status for
     * @param enabled  Whether the Iris settings should be enabled
     */
    private void setExerciseSettingsEnabled(Exercise exercise, boolean enabled) {
        var exerciseSettings = getRawIrisSettingsFor(exercise);
        if (exercise instanceof ProgrammingExercise) {
            exerciseSettings.getIrisChatSettings().setEnabled(enabled);
        }
        else if (exercise instanceof TextExercise) {
            exerciseSettings.getIrisTextExerciseChatSettings().setEnabled(enabled);
        }
        irisSettingsRepository.save(exerciseSettings);
    }

    /**
     * Convert the category JSON strings of an exercise to a set of category names.
     *
     * @param exerciseCategories The set of category JSON strings
     * @return The set of category names
     */
    private Set<String> getCategoryNames(Set<String> exerciseCategories) {
        var categories = new HashSet<String>();
        for (var categoryJson : exerciseCategories) {
            try {
                var category = objectMapper.readTree(categoryJson);
                categories.add(category.get("category").asText());
            }
            catch (JsonProcessingException e) {
                return new HashSet<>();
            }
        }
        return categories;
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
        // @formatter:off
        existingSettings.setIrisChatSettings(irisSubSettingsService.update(
                existingSettings.getIrisChatSettings(),
                settingsUpdate.getIrisChatSettings(),
                parentSettings.irisChatSettings(),
                EXERCISE
        ));
        existingSettings.setIrisTextExerciseChatSettings(irisSubSettingsService.update(
                existingSettings.getIrisTextExerciseChatSettings(),
                settingsUpdate.getIrisTextExerciseChatSettings(),
                parentSettings.irisTextExerciseChatSettings(),
                EXERCISE
        ));
        // @formatter:on
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

        // @formatter:off
        return new IrisCombinedSettingsDTO(
                irisSubSettingsService.combineChatSettings(settingsList, false),
                irisSubSettingsService.combineTextExerciseChatSettings(settingsList, false),
                irisSubSettingsService.combineLectureIngestionSubSettings(settingsList, false),
                irisSubSettingsService.combineCompetencyGenerationSettings(settingsList, false)
        );
        // @formatter:on
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

        // @formatter:off
        return new IrisCombinedSettingsDTO(
                irisSubSettingsService.combineChatSettings(settingsList, minimal),
                irisSubSettingsService.combineTextExerciseChatSettings(settingsList, minimal),
                irisSubSettingsService.combineLectureIngestionSubSettings(settingsList, minimal),
                irisSubSettingsService.combineCompetencyGenerationSettings(settingsList, minimal)
        );
        // @formatter:on
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

        // @formatter:off
        return new IrisCombinedSettingsDTO(
                irisSubSettingsService.combineChatSettings(settingsList, minimal),
                irisSubSettingsService.combineTextExerciseChatSettings(settingsList, minimal),
                irisSubSettingsService.combineLectureIngestionSubSettings(settingsList, minimal),
                irisSubSettingsService.combineCompetencyGenerationSettings(settingsList, minimal)
        );
        // @formatter:on
    }

    /**
     * Check if we have to show minimal settings for an exercise. Editors can see the full settings, students only the reduced settings.
     *
     * @param exercise The exercise to check
     * @param user     The user to check
     * @return Whether we have to show the user the minimal settings
     */
    public boolean shouldShowMinimalSettings(Exercise exercise, User user) {
        return !authCheckService.isAtLeastEditorForExercise(exercise, user);
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
        settings.setIrisLectureIngestionSettings(new IrisLectureIngestionSubSettings());
        settings.setIrisChatSettings(new IrisChatSubSettings());
        settings.setIrisCompetencyGenerationSettings(new IrisCompetencyGenerationSubSettings());
        settings.setIrisTextExerciseChatSettings(new IrisTextExerciseChatSubSettings());
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
        settings.setIrisTextExerciseChatSettings(new IrisTextExerciseChatSubSettings());
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
            case TEXT_EXERCISE_CHAT -> settings.irisTextExerciseChatSettings().enabled();
            case COMPETENCY_GENERATION -> settings.irisCompetencyGenerationSettings().enabled();
            case LECTURE_INGESTION -> settings.irisLectureIngestionSettings().enabled();
        };
    }
}
