package de.tum.cit.aet.artemis.iris.service.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.domain.settings.HasEnabledCategories;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCompetencyGenerationSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisExerciseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisFaqIngestionSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisLectureChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisLectureIngestionSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisProgrammingExerciseChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSettingsType;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisTextExerciseChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisTutorSuggestionSubSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedCompetencyGenerationSubSettingsDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedCourseChatSubSettingsDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedFaqIngestionSubSettingsDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedLectureChatSubSettingsDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedLectureIngestionSubSettingsDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedProgrammingExerciseChatSubSettingsDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedTextExerciseChatSubSettingsDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedTutorSuggestionSubSettingsDTO;

/**
 * Service for handling {@link IrisSubSettings} objects.
 * This server provides methods to update and combine sub settings objects.
 * See {@link IrisSettingsService} for more information about handling {@link IrisSettings}.
 */
@Lazy
@Service
@Profile(PROFILE_IRIS)
public class IrisSubSettingsService {

    private final AuthorizationCheckService authCheckService;

    public IrisSubSettingsService(AuthorizationCheckService authCheckService) {
        this.authCheckService = authCheckService;
    }

    /**
     * Updates a chat sub settings object.
     * If the new settings are null, the current settings will be deleted (except if the parent settings are null == if the settings are global).
     * Special notes:
     * - If the user is not an admin the rate limit will not be updated.
     * - If the user is not an admin the allowed models will not be updated.
     * - If the user is not an admin the preferred model will only be updated if it is included in the allowed models.
     * - If the user is not an admin the disabled proactive events will only be updated if the settings are exercise or course settings.
     *
     * @param currentSettings Current chat sub settings.
     * @param newSettings     Updated chat sub settings.
     * @param parentSettings  Parent chat sub settings.
     * @param settingsType    Type of the settings the sub settings belong to.
     * @return Updated chat sub settings.
     */
    public IrisProgrammingExerciseChatSubSettings update(IrisProgrammingExerciseChatSubSettings currentSettings, IrisProgrammingExerciseChatSubSettings newSettings,
            IrisCombinedProgrammingExerciseChatSubSettingsDTO parentSettings, IrisSettingsType settingsType) {
        if (newSettings == null) {
            if (parentSettings == null) {
                throw new IllegalArgumentException("Cannot delete the chat settings");
            }
            return null;
        }
        if (currentSettings == null) {
            currentSettings = new IrisProgrammingExerciseChatSubSettings();
        }
        currentSettings.setEnabled(newSettings.isEnabled());
        if (settingsType == IrisSettingsType.COURSE) {
            var enabledForCategories = newSettings.getEnabledForCategories();
            currentSettings.setEnabledForCategories(enabledForCategories);
        }
        if (authCheckService.isAdmin()) {
            currentSettings.setRateLimit(newSettings.getRateLimit());
            currentSettings.setRateLimitTimeframeHours(newSettings.getRateLimitTimeframeHours());
        }
        if (settingsType == IrisSettingsType.COURSE || settingsType == IrisSettingsType.EXERCISE || authCheckService.isAdmin()) {
            currentSettings.setDisabledProactiveEvents(newSettings.getDisabledProactiveEvents());
        }
        currentSettings.setAllowedVariants(selectAllowedVariants(currentSettings.getAllowedVariants(), newSettings.getAllowedVariants()));
        currentSettings.setSelectedVariant(validateSelectedVariant(currentSettings.getSelectedVariant(), newSettings.getSelectedVariant(), currentSettings.getAllowedVariants(),
                parentSettings != null ? parentSettings.allowedVariants() : null));
        currentSettings.setCustomInstructions(validateCustomInstructions(newSettings.getCustomInstructions()));
        return currentSettings;
    }

    /**
     * Updates a text exercise chat sub settings object.
     *
     * @param currentSettings Current chat sub settings.
     * @param newSettings     Updated chat sub settings.
     * @param parentSettings  Parent chat sub settings.
     * @param settingsType    Type of the settings the sub settings belong to.
     * @return Updated chat sub settings.
     */
    public IrisTextExerciseChatSubSettings update(IrisTextExerciseChatSubSettings currentSettings, IrisTextExerciseChatSubSettings newSettings,
            IrisCombinedTextExerciseChatSubSettingsDTO parentSettings, IrisSettingsType settingsType) {
        if (newSettings == null) {
            if (parentSettings == null) {
                throw new IllegalArgumentException("Cannot delete the chat settings");
            }
            return null;
        }
        if (currentSettings == null) {
            currentSettings = new IrisTextExerciseChatSubSettings();
        }
        currentSettings.setEnabled(newSettings.isEnabled());
        if (settingsType == IrisSettingsType.COURSE) {
            var enabledForCategories = newSettings.getEnabledForCategories();
            currentSettings.setEnabledForCategories(enabledForCategories);
        }
        if (authCheckService.isAdmin()) {
            currentSettings.setRateLimit(newSettings.getRateLimit());
            currentSettings.setRateLimitTimeframeHours(newSettings.getRateLimitTimeframeHours());
        }
        currentSettings.setAllowedVariants(selectAllowedVariants(currentSettings.getAllowedVariants(), newSettings.getAllowedVariants()));
        currentSettings.setSelectedVariant(validateSelectedVariant(currentSettings.getSelectedVariant(), newSettings.getSelectedVariant(), currentSettings.getAllowedVariants(),
                parentSettings != null ? parentSettings.allowedVariants() : null));
        currentSettings.setCustomInstructions(validateCustomInstructions(newSettings.getCustomInstructions()));
        return currentSettings;
    }

    /**
     * Updates a course chat sub settings object.
     *
     * @param currentSettings Current chat sub settings.
     * @param newSettings     Updated chat sub settings.
     * @param parentSettings  Parent chat sub settings.
     * @param settingsType    Type of the settings the sub settings belong to.
     * @return Updated chat sub settings.
     */
    public IrisCourseChatSubSettings update(IrisCourseChatSubSettings currentSettings, IrisCourseChatSubSettings newSettings, IrisCombinedCourseChatSubSettingsDTO parentSettings,
            IrisSettingsType settingsType) {
        if (newSettings == null) {
            if (parentSettings == null) {
                throw new IllegalArgumentException("Cannot delete the course chat settings");
            }
            return null;
        }
        if (currentSettings == null) {
            currentSettings = new IrisCourseChatSubSettings();
        }

        if (isCourseOrGlobalSettings(settingsType)) {
            currentSettings.setEnabled(newSettings.isEnabled());

            if (authCheckService.isAdmin()) {
                currentSettings.setRateLimit(newSettings.getRateLimit());
                currentSettings.setRateLimitTimeframeHours(newSettings.getRateLimitTimeframeHours());
            }

            currentSettings.setAllowedVariants(selectAllowedVariants(currentSettings.getAllowedVariants(), newSettings.getAllowedVariants()));
            currentSettings.setSelectedVariant(validateSelectedVariant(currentSettings.getSelectedVariant(), newSettings.getSelectedVariant(), currentSettings.getAllowedVariants(),
                    parentSettings != null ? parentSettings.allowedVariants() : null));
        }
        currentSettings.setCustomInstructions(validateCustomInstructions(newSettings.getCustomInstructions()));

        return currentSettings;
    }

    private static boolean isCourseOrGlobalSettings(IrisSettingsType settingsType) {
        return settingsType == IrisSettingsType.COURSE || settingsType == IrisSettingsType.GLOBAL;
    }

    /**
     * Updates a Lecture Ingestion sub settings object.
     * If the new settings are null, the current settings will be deleted (except if the parent settings are null == if the settings are global).
     * Special notes:
     *
     * @param currentSettings Current Lecture Ingestion sub settings.
     * @param newSettings     Updated Lecture Ingestion sub settings.
     * @param parentSettings  Parent Lecture Ingestion sub settings.
     * @param settingsType    Type of the settings the sub settings belong to.
     * @return Updated Lecture Ingestion sub settings.
     */
    public IrisLectureIngestionSubSettings update(IrisLectureIngestionSubSettings currentSettings, IrisLectureIngestionSubSettings newSettings,
            IrisCombinedLectureIngestionSubSettingsDTO parentSettings, IrisSettingsType settingsType) {
        if (newSettings == null) {
            if (parentSettings == null) {
                throw new IllegalArgumentException("Cannot delete the Lecture Ingestion settings");
            }
            return null;
        }
        if (currentSettings == null) {
            currentSettings = new IrisLectureIngestionSubSettings();
        }

        if (isCourseOrGlobalSettings(settingsType)) {
            currentSettings.setEnabled(newSettings.isEnabled());
            currentSettings.setAutoIngestOnLectureAttachmentUpload(newSettings.getAutoIngestOnLectureAttachmentUpload());
            currentSettings.setAllowedVariants(selectAllowedVariants(currentSettings.getAllowedVariants(), newSettings.getAllowedVariants()));
            currentSettings.setSelectedVariant(validateSelectedVariant(currentSettings.getSelectedVariant(), newSettings.getSelectedVariant(), currentSettings.getAllowedVariants(),
                    parentSettings != null ? parentSettings.allowedVariants() : null));
        }

        return currentSettings;
    }

    /**
     * Updates a Lecture Chat sub settings object.
     * If the new settings are null, the current settings will be deleted (except if the parent settings are null == if the settings are global).
     * Special notes:
     *
     * @param currentSettings Current Lecture Chat sub settings.
     * @param newSettings     Updated Lecture Chat sub settings.
     * @param parentSettings  Parent Lecture Chat sub settings.
     * @param settingsType    Type of the settings the sub settings belong to.
     * @return Updated Lecture Chat sub settings.
     */
    public IrisLectureChatSubSettings update(IrisLectureChatSubSettings currentSettings, IrisLectureChatSubSettings newSettings,
            IrisCombinedLectureChatSubSettingsDTO parentSettings, IrisSettingsType settingsType) {
        if (newSettings == null) {
            if (parentSettings == null) {
                throw new IllegalArgumentException("Cannot delete the Lecture Chat settings");
            }
            return null;
        }
        if (currentSettings == null) {
            currentSettings = new IrisLectureChatSubSettings();
        }

        if (isCourseOrGlobalSettings(settingsType)) {
            currentSettings.setEnabled(newSettings.isEnabled());
            if (authCheckService.isAdmin()) {
                currentSettings.setRateLimit(newSettings.getRateLimit());
                currentSettings.setRateLimitTimeframeHours(newSettings.getRateLimitTimeframeHours());
            }
            currentSettings.setAllowedVariants(selectAllowedVariants(currentSettings.getAllowedVariants(), newSettings.getAllowedVariants()));
            currentSettings.setSelectedVariant(validateSelectedVariant(currentSettings.getSelectedVariant(), newSettings.getSelectedVariant(), currentSettings.getAllowedVariants(),
                    parentSettings != null ? parentSettings.allowedVariants() : null));
        }

        currentSettings.setCustomInstructions(validateCustomInstructions(newSettings.getCustomInstructions()));

        return currentSettings;
    }

    /**
     * Updates a FAQ Ingestion sub settings object.
     * If the new settings are null, the current settings will be deleted (except if the parent settings are null == if the settings are global).
     * Special notes: if the new Settings are null, we will return null. That means the sub-settings will be deleted.
     *
     * @param currentSettings Current FAQ Ingestion sub settings.
     * @param newSettings     Updated FAQ Ingestion sub settings.
     * @param parentSettings  Parent FAQ Ingestion sub settings.
     * @param settingsType    Type of the settings the sub settings belong to.
     * @return Updated FAQ Ingestion sub settings.
     */
    public IrisFaqIngestionSubSettings update(IrisFaqIngestionSubSettings currentSettings, IrisFaqIngestionSubSettings newSettings,
            IrisCombinedFaqIngestionSubSettingsDTO parentSettings, IrisSettingsType settingsType) {
        if (newSettings == null) {
            if (parentSettings == null) {
                throw new IllegalArgumentException("Cannot delete the FAQ Ingestion settings");
            }
            return null;
        }
        if (currentSettings == null) {
            currentSettings = new IrisFaqIngestionSubSettings();
        }

        if (isCourseOrGlobalSettings(settingsType)) {
            currentSettings.setEnabled(newSettings.isEnabled());
            currentSettings.setAutoIngestOnFaqCreation(newSettings.getAutoIngestOnFaqCreation());
            currentSettings.setAllowedVariants(selectAllowedVariants(currentSettings.getAllowedVariants(), newSettings.getAllowedVariants()));
            currentSettings.setSelectedVariant(validateSelectedVariant(currentSettings.getSelectedVariant(), newSettings.getSelectedVariant(), currentSettings.getAllowedVariants(),
                    parentSettings != null ? parentSettings.allowedVariants() : null));
        }

        return currentSettings;
    }

    /**
     * Updates a Competency Generation sub settings object.
     * If the new settings are null, the current settings will be deleted (except if the parent settings are null == if the settings are global).
     * Special notes:
     * - If the user is not an admin the allowed models will not be updated.
     * - If the user is not an admin the preferred model will only be updated if it is included in the allowed models.
     *
     * @param currentSettings Current Competency Generation sub settings.
     * @param newSettings     Updated Competency Generation sub settings.
     * @param parentSettings  Parent Competency Generation sub settings.
     * @param settingsType    Type of the settings the sub settings belong to.
     * @return Updated Competency Generation sub settings.
     */
    public IrisCompetencyGenerationSubSettings update(IrisCompetencyGenerationSubSettings currentSettings, IrisCompetencyGenerationSubSettings newSettings,
            IrisCombinedCompetencyGenerationSubSettingsDTO parentSettings, IrisSettingsType settingsType) {
        if (newSettings == null) {
            if (parentSettings == null) {
                throw new IllegalArgumentException("Cannot delete the Competency Generation settings");
            }
            return null;
        }
        if (currentSettings == null) {
            currentSettings = new IrisCompetencyGenerationSubSettings();
        }

        if (isCourseOrGlobalSettings(settingsType)) {
            currentSettings.setEnabled(newSettings.isEnabled());
            currentSettings.setAllowedVariants(selectAllowedVariants(currentSettings.getAllowedVariants(), newSettings.getAllowedVariants()));
            currentSettings.setSelectedVariant(validateSelectedVariant(currentSettings.getSelectedVariant(), newSettings.getSelectedVariant(), currentSettings.getAllowedVariants(),
                    parentSettings != null ? parentSettings.allowedVariants() : null));
        }

        return currentSettings;
    }

    /**
     * Updates a Tutor Suggestion sub settings object.
     * If the new settings are null, the current settings will be deleted (except if the parent settings are null == if the settings are global).
     * Special notes:
     * - If user is not an admin the allowed models will not be updated.
     * - If user is not an admin the preferred model will only be updated if it is included in the allowed models.
     *
     * @param currentSettings Current Tutor Suggestion sub settings.
     * @param newSettings     Updated Tutor Suggestion sub settings.
     * @param parentSettings  Parent Tutor Suggestion sub settings.
     * @param settingsType    Type of the settings the sub settings belong to.
     * @return Updated Tutor Suggestion sub settings.
     */
    public IrisTutorSuggestionSubSettings update(IrisTutorSuggestionSubSettings currentSettings, IrisTutorSuggestionSubSettings newSettings,
            IrisCombinedTutorSuggestionSubSettingsDTO parentSettings, IrisSettingsType settingsType) {
        if (newSettings == null) {
            if (parentSettings == null) {
                throw new IllegalArgumentException("Cannot delete the Tutor Suggestion settings");
            }
            return null;
        }
        if (currentSettings == null) {
            currentSettings = new IrisTutorSuggestionSubSettings();
        }

        if (isCourseOrGlobalSettings(settingsType)) {
            currentSettings.setEnabled(newSettings.isEnabled());
            currentSettings.setAllowedVariants(selectAllowedVariants(currentSettings.getAllowedVariants(), newSettings.getAllowedVariants()));
            currentSettings.setSelectedVariant(validateSelectedVariant(currentSettings.getSelectedVariant(), newSettings.getSelectedVariant(), currentSettings.getAllowedVariants(),
                    parentSettings != null ? parentSettings.allowedVariants() : null));
        }

        return currentSettings;
    }

    /**
     * Filters the allowed models of a sub settings object.
     * If the user is an admin, all models are allowed.
     * Otherwise, only models that are allowed by the parent settings or the current settings are allowed.
     *
     * @param allowedVariants        The allowed models of the current settings.
     * @param updatedAllowedVariants The allowed models of the updated settings.
     * @return The filtered allowed models.
     */
    private SortedSet<String> selectAllowedVariants(SortedSet<String> allowedVariants, SortedSet<String> updatedAllowedVariants) {
        return authCheckService.isAdmin() ? updatedAllowedVariants : allowedVariants;
    }

    /**
     * Validates the preferred model of a sub settings object.
     * If the user is an admin, all models are allowed.
     * Otherwise, only models that are allowed by the current settings are allowed.
     *
     * @param selectedVariant       The preferred model of the current settings.
     * @param newSelectedVariant    The preferred model of the updated settings.
     * @param allowedVariants       The allowed models of the current settings.
     * @param parentAllowedVariants The allowed models of the parent settings.
     * @return The validated preferred model.
     */
    private String validateSelectedVariant(String selectedVariant, String newSelectedVariant, Set<String> allowedVariants, Set<String> parentAllowedVariants) {
        if (newSelectedVariant == null || newSelectedVariant.isBlank()) {
            return null;
        }
        var canChangeSelectedVariant = authCheckService.isAdmin() || (allowedVariants != null && !allowedVariants.isEmpty() && allowedVariants.contains(newSelectedVariant))
                || ((allowedVariants == null || allowedVariants.isEmpty()) && parentAllowedVariants != null && parentAllowedVariants.contains(newSelectedVariant));
        if (canChangeSelectedVariant) {
            return newSelectedVariant;
        }

        return selectedVariant;
    }

    /**
     * Validates the custom instructions length of a sub settings object.
     *
     * @param customInstructions The custom instructions of the updated settings.
     * @return The validated custom instructions.
     */
    private String validateCustomInstructions(String customInstructions) {
        if (customInstructions == null || customInstructions.isBlank()) {
            return null;
        }
        if (customInstructions.length() > IRIS_CUSTOM_INSTRUCTIONS_MAX_LENGTH) {
            throw new BadRequestAlertException("Custom instructions are too long", "IrisSettings", "customInstructionsTooLong");
        }
        return customInstructions;
    }

    /**
     * Combines the chat settings of multiple {@link IrisSettings} objects.
     * If minimal is true, the returned object will only contain the enabled and rateLimit fields.
     * The minimal version can safely be sent to students.
     *
     * @param settingsList List of {@link IrisSettings} objects to combine.
     * @param minimal      Whether to return a minimal version of the combined settings.
     * @return Combined chat settings.
     */
    public IrisCombinedProgrammingExerciseChatSubSettingsDTO combineChatSettings(ArrayList<IrisSettings> settingsList, boolean minimal) {
        var enabled = getCombinedEnabled(settingsList, IrisSettings::getIrisProgrammingExerciseChatSettings);
        var rateLimit = getCombinedRateLimit(settingsList);
        var allowedVariants = !minimal ? getCombinedAllowedVariants(settingsList, IrisSettings::getIrisProgrammingExerciseChatSettings) : null;
        var selectedVariant = !minimal ? getCombinedSelectedVariant(settingsList, IrisSettings::getIrisProgrammingExerciseChatSettings, allowedVariants) : null;
        var enabledForCategories = !minimal ? getCombinedEnabledForCategories(settingsList, IrisSettings::getIrisProgrammingExerciseChatSettings) : null;
        var disabledForEvents = !minimal ? getCombinedDisabledForEvents(settingsList, IrisSettings::getIrisProgrammingExerciseChatSettings) : null;
        var customInstructions = minimal ? null : getCombinedCustomInstructions(settingsList, IrisSettings::getIrisProgrammingExerciseChatSettings);
        return new IrisCombinedProgrammingExerciseChatSubSettingsDTO(enabled, rateLimit, null, allowedVariants, selectedVariant, enabledForCategories, disabledForEvents,
                customInstructions);
    }

    /**
     * Combines the chat settings of multiple {@link IrisSettings} objects.
     * If minimal is true, the returned object will only contain the enabled and rateLimit fields.
     * The minimal version can safely be sent to students.
     *
     * @param settingsList List of {@link IrisSettings} objects to combine.
     * @param minimal      Whether to return a minimal version of the combined settings.
     * @return Combined chat settings.
     */
    public IrisCombinedTextExerciseChatSubSettingsDTO combineTextExerciseChatSettings(ArrayList<IrisSettings> settingsList, boolean minimal) {
        var enabled = getCombinedEnabled(settingsList, IrisSettings::getIrisTextExerciseChatSettings);
        var rateLimit = getCombinedRateLimit(settingsList);
        var allowedVariants = !minimal ? getCombinedAllowedVariants(settingsList, IrisSettings::getIrisTextExerciseChatSettings) : null;
        var selectedVariant = !minimal ? getCombinedSelectedVariant(settingsList, IrisSettings::getIrisTextExerciseChatSettings, allowedVariants) : null;
        var enabledForCategories = !minimal ? getCombinedEnabledForCategories(settingsList, IrisSettings::getIrisTextExerciseChatSettings) : null;
        var customInstructions = minimal ? null : getCombinedCustomInstructions(settingsList, IrisSettings::getIrisTextExerciseChatSettings);
        return new IrisCombinedTextExerciseChatSubSettingsDTO(enabled, rateLimit, null, allowedVariants, selectedVariant, enabledForCategories, customInstructions);
    }

    /**
     * Combines the lecture chat settings of multiple {@link IrisSettings} objects.
     * If minimal is true, the returned object will only contain the enabled and rateLimit fields.
     * The minimal version can safely be sent to students.
     *
     * @param settingsList List of {@link IrisSettings} objects to combine.
     * @param minimal      Whether to return a minimal version of the combined settings.
     * @return Combined lecture chat settings.
     */
    public IrisCombinedLectureChatSubSettingsDTO combineLectureChatSettings(ArrayList<IrisSettings> settingsList, boolean minimal) {
        boolean enabled = getCombinedEnabled(settingsList, IrisSettings::getIrisLectureChatSettings);
        Integer rateLimit = getCombinedRateLimit(settingsList);
        SortedSet<String> allowedVariants = !minimal ? getCombinedAllowedVariants(settingsList, IrisSettings::getIrisLectureChatSettings) : null;
        String selectedVariant = !minimal ? getCombinedSelectedVariant(settingsList, IrisSettings::getIrisLectureChatSettings, allowedVariants) : null;
        var customInstructions = minimal ? null : getCombinedCustomInstructions(settingsList, IrisSettings::getIrisLectureChatSettings);
        return new IrisCombinedLectureChatSubSettingsDTO(enabled, rateLimit, null, customInstructions, allowedVariants, selectedVariant);
    }

    /**
     * Combines the chat settings of multiple {@link IrisSettings} objects.
     * If minimal is true, the returned object will only contain the enabled and rateLimit fields.
     * The minimal version can safely be sent to students.
     *
     * @param settingsList List of {@link IrisSettings} objects to combine.
     * @param minimal      Whether to return a minimal version of the combined settings.
     * @return Combined chat settings.
     */
    public IrisCombinedCourseChatSubSettingsDTO combineCourseChatSettings(ArrayList<IrisSettings> settingsList, boolean minimal) {
        var enabled = getCombinedEnabled(settingsList, IrisSettings::getIrisCourseChatSettings);
        var rateLimit = getCombinedRateLimit(settingsList);
        var allowedVariants = !minimal ? getCombinedAllowedVariants(settingsList, IrisSettings::getIrisCourseChatSettings) : null;
        var selectedVariant = !minimal ? getCombinedSelectedVariant(settingsList, IrisSettings::getIrisCourseChatSettings, allowedVariants) : null;
        var customInstructions = minimal ? null : getCombinedCustomInstructions(settingsList, IrisSettings::getIrisCourseChatSettings);
        return new IrisCombinedCourseChatSubSettingsDTO(enabled, rateLimit, null, allowedVariants, selectedVariant, customInstructions);
    }

    /**
     * Combines the Lecture Ingestion settings of multiple {@link IrisSettings} objects.
     * If minimal is true, the returned object will only contain the enabled and rateLimit fields.
     * The minimal version can safely be sent to students.
     *
     * @param settingsList List of {@link IrisSettings} objects to combine.
     * @param minimal      Whether to return a minimal version of the combined settings.
     * @return Combined Lecture Ingestion settings.
     */
    public IrisCombinedLectureIngestionSubSettingsDTO combineLectureIngestionSubSettings(ArrayList<IrisSettings> settingsList, boolean minimal) {
        var enabled = getCombinedEnabled(settingsList, IrisSettings::getIrisLectureIngestionSettings);
        var allowedVariants = !minimal ? getCombinedAllowedVariants(settingsList, IrisSettings::getIrisLectureIngestionSettings) : null;
        var selectedVariant = !minimal ? getCombinedSelectedVariant(settingsList, IrisSettings::getIrisLectureIngestionSettings, allowedVariants) : null;

        var autoIngestOnLectureAttachmentUpload = true;
        for (var settings : settingsList) {
            if (settings == null) {
                continue;
            }
            var lectureIngestionSettings = settings.getIrisLectureIngestionSettings();
            if (lectureIngestionSettings != null && !lectureIngestionSettings.getAutoIngestOnLectureAttachmentUpload()) {
                autoIngestOnLectureAttachmentUpload = false;
                break;
            }
        }

        return new IrisCombinedLectureIngestionSubSettingsDTO(enabled, autoIngestOnLectureAttachmentUpload, allowedVariants, selectedVariant);
    }

    /**
     * Combines the FAQ Ingestion settings of multiple {@link IrisSettings} objects.
     * If minimal is true, the returned object will only contain the enabled and rateLimit fields.
     * The minimal version can safely be sent to students.
     *
     * @param settingsList List of {@link IrisSettings} objects to combine.
     * @param minimal      Whether to return a minimal version of the combined settings.
     * @return Combined Lecture Ingestion settings.
     */
    public IrisCombinedFaqIngestionSubSettingsDTO combineFaqIngestionSubSettings(ArrayList<IrisSettings> settingsList, boolean minimal) {
        var enabled = getCombinedEnabled(settingsList, IrisSettings::getIrisFaqIngestionSettings);
        var allowedVariants = !minimal ? getCombinedAllowedVariants(settingsList, IrisSettings::getIrisFaqIngestionSettings) : null;
        var selectedVariant = !minimal ? getCombinedSelectedVariant(settingsList, IrisSettings::getIrisFaqIngestionSettings, allowedVariants) : null;

        var autoIngestOnFaqCreation = true;
        for (var settings : settingsList) {
            if (settings == null) {
                continue;
            }
            var faqIngestionSettings = settings.getIrisFaqIngestionSettings();
            if (faqIngestionSettings != null && !faqIngestionSettings.getAutoIngestOnFaqCreation()) {
                autoIngestOnFaqCreation = false;
                break;
            }
        }

        return new IrisCombinedFaqIngestionSubSettingsDTO(enabled, autoIngestOnFaqCreation, allowedVariants, selectedVariant);
    }

    /**
     * Combines the Competency Generation settings of multiple {@link IrisSettings} objects.
     * If minimal is true, the returned object will only contain the enabled field.
     * The minimal version can safely be sent to students.
     *
     * @param settingsList List of {@link IrisSettings} objects to combine.
     * @param minimal      Whether to return a minimal version of the combined settings.
     * @return Combined Competency Generation settings.
     */
    public IrisCombinedCompetencyGenerationSubSettingsDTO combineCompetencyGenerationSettings(ArrayList<IrisSettings> settingsList, boolean minimal) {
        var actualSettingsList = settingsList.stream().filter(settings -> !(settings instanceof IrisExerciseSettings)).toList();
        var enabled = getCombinedEnabled(actualSettingsList, IrisSettings::getIrisCompetencyGenerationSettings);
        var allowedVariants = !minimal ? getCombinedAllowedVariants(actualSettingsList, IrisSettings::getIrisCompetencyGenerationSettings) : null;
        var selectedVariant = !minimal ? getCombinedSelectedVariant(actualSettingsList, IrisSettings::getIrisCompetencyGenerationSettings, allowedVariants) : null;
        return new IrisCombinedCompetencyGenerationSubSettingsDTO(enabled, allowedVariants, selectedVariant);
    }

    /**
     * Combines the Tutor Suggestion settings of multiple {@link IrisSettings} objects.
     *
     * @param settingsList List of {@link IrisSettings} objects to combine.
     * @param minimal      Whether to return a minimal version of the combined settings.
     * @return Combined Tutor Suggestion settings.
     */
    public IrisCombinedTutorSuggestionSubSettingsDTO combineTutorSuggestionSettings(ArrayList<IrisSettings> settingsList, boolean minimal) {
        var enabled = getCombinedEnabled(settingsList, IrisSettings::getIrisTutorSuggestionSettings);
        var allowedVariants = !minimal ? getCombinedAllowedVariants(settingsList, IrisSettings::getIrisTutorSuggestionSettings) : null;
        var selectedVariant = !minimal ? getCombinedSelectedVariant(settingsList, IrisSettings::getIrisTutorSuggestionSettings, allowedVariants) : null;
        return new IrisCombinedTutorSuggestionSubSettingsDTO(enabled, allowedVariants, selectedVariant);
    }

    /**
     * Combines the enabled field of multiple {@link IrisSettings} objects.
     * Simply &&s all enabled fields together.
     *
     * @param settingsList        List of {@link IrisSettings} objects to combine.
     * @param subSettingsFunction Function to get the sub settings from an IrisSettings object.
     * @return Combined enabled field.
     */
    private <T extends IrisSubSettings> boolean getCombinedEnabled(List<IrisSettings> settingsList, Function<IrisSettings, T> subSettingsFunction) {
        if (settingsList == null || settingsList.isEmpty() || settingsList.stream().allMatch(Objects::isNull)) {
            return false;
        }
        for (var irisSettings : settingsList) {
            if (irisSettings == null) {
                continue;
            }
            var settings = subSettingsFunction.apply(irisSettings);
            if (settings == null) {
                continue;
            }
            if (!settings.isEnabled()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Combines the rateLimit field of multiple {@link IrisSettings} objects.
     * Simply takes the minimum rateLimit.
     *
     * @param settingsList List of {@link IrisSettings} objects to combine.
     * @return Combined rateLimit field.
     */
    private Integer getCombinedRateLimit(List<IrisSettings> settingsList) {
        return settingsList.stream().filter(Objects::nonNull).map(IrisSettings::getIrisProgrammingExerciseChatSettings).filter(Objects::nonNull)
                .map(IrisProgrammingExerciseChatSubSettings::getRateLimit).filter(rateLimit -> rateLimit != null && rateLimit >= 0).min(Comparator.comparingInt(Integer::intValue))
                .orElse(null);
    }

    /**
     * Combines the allowedVariants field of multiple {@link IrisSettings} objects.
     * Simply takes the last allowedVariants.
     *
     * @param settingsList        List of {@link IrisSettings} objects to combine.
     * @param subSettingsFunction Function to get the sub settings from an IrisSettings object.
     * @return Combined allowedVariants field.
     */
    private SortedSet<String> getCombinedAllowedVariants(List<IrisSettings> settingsList, Function<IrisSettings, IrisSubSettings> subSettingsFunction) {
        return settingsList.stream().filter(Objects::nonNull).map(subSettingsFunction).filter(Objects::nonNull).map(IrisSubSettings::getAllowedVariants).filter(Objects::nonNull)
                .filter(variants -> !variants.isEmpty()).reduce((first, second) -> second).orElse(new TreeSet<>());
    }

    /**
     * Combines the selectedVariant field of multiple {@link IrisSettings} objects.
     * Simply takes the last selectedVariant.
     *
     * @param settingsList        List of {@link IrisSettings} objects to combine.
     * @param subSettingsFunction Function to get the sub settings from an IrisSettings object.
     * @return Combined selectedVariant field.
     */
    private String getCombinedSelectedVariant(List<IrisSettings> settingsList, Function<IrisSettings, IrisSubSettings> subSettingsFunction, Collection<String> allowedVariants) {
        return settingsList.stream().filter(Objects::nonNull).map(subSettingsFunction).filter(Objects::nonNull).map(IrisSubSettings::getSelectedVariant)
                .filter(variant -> variant != null && !variant.isBlank() && (allowedVariants == null || allowedVariants.contains(variant))).reduce((first, second) -> second)
                .orElse(null);
    }

    /**
     * Combines the enabledForCategories field of multiple {@link IrisSettings} objects.
     * Simply &&s all enabledForCategories fields together.
     *
     * @param settingsList        List of {@link IrisSettings} objects to combine.
     * @param subSettingsFunction Function to get the sub settings from an IrisSettings object.
     * @return Combined enabledForCategories field.
     */
    private SortedSet<String> getCombinedEnabledForCategories(List<IrisSettings> settingsList, Function<IrisSettings, HasEnabledCategories> subSettingsFunction) {
        return settingsList.stream().filter(Objects::nonNull).filter(settings -> settings instanceof IrisCourseSettings).map(subSettingsFunction).filter(Objects::nonNull)
                .map(HasEnabledCategories::getEnabledForCategories).filter(Objects::nonNull).filter(models -> !models.isEmpty()).reduce((first, second) -> second)
                .orElse(new TreeSet<>());
    }

    /**
     * Combines the customInstructions field of multiple {@link IrisSettings} objects.
     * Simply takes the most specific non-empty value.
     *
     * @param settingsList        List of {@link IrisSettings} objects to combine.
     * @param subSettingsFunction Function to get the sub settings from an IrisSettings object.
     * @return Combined customInstructions field.
     */
    private <T extends IrisSubSettings> String getCombinedCustomInstructions(List<IrisSettings> settingsList, Function<IrisSettings, T> subSettingsFunction) {
        // Use most specific non-blank customInstructions
        return settingsList.stream().filter(Objects::nonNull).map(subSettingsFunction).filter(Objects::nonNull).map(this::getCustomInstructionsFromSubSettings)
                .filter(instructions -> instructions != null && !instructions.isBlank()).reduce((first, second) -> second) // Take the last non-empty value (most specific)
                .orElse(null);
    }

    /**
     * Gets the customInstructions from a sub settings object, handling different sub-settings types.
     *
     * @param subSettings The sub settings object
     * @return The customInstructions or null if not applicable
     */
    private String getCustomInstructionsFromSubSettings(IrisSubSettings subSettings) {
        // TODO: Introduce intermediary abstract class for all chat settings types
        if (subSettings instanceof IrisProgrammingExerciseChatSubSettings settings) {
            return settings.getCustomInstructions();
        }
        else if (subSettings instanceof IrisTextExerciseChatSubSettings settings) {
            return settings.getCustomInstructions();
        }
        else if (subSettings instanceof IrisCourseChatSubSettings settings) {
            return settings.getCustomInstructions();
        }
        else if (subSettings instanceof IrisLectureChatSubSettings settings) {
            return settings.getCustomInstructions();
        }
        return null;
    }

    /**
     * Combines the disabledProactiveEvents field of multiple {@link IrisSettings} objects.
     * Simply takes the last disabledProactiveEvents.
     *
     * @param settingsList List of {@link IrisSettings} objects to combine.
     * @return Combined disabledProactiveEvents field.
     */
    private SortedSet<String> getCombinedDisabledForEvents(List<IrisSettings> settingsList, Function<IrisSettings, IrisProgrammingExerciseChatSubSettings> subSettingsFunction) {
        return settingsList.stream().filter(Objects::nonNull).map(subSettingsFunction).filter(Objects::nonNull)
                .map(IrisProgrammingExerciseChatSubSettings::getDisabledProactiveEvents).filter(Objects::nonNull).reduce((first, second) -> {
                    var result = new TreeSet<>(second);
                    result.addAll(first);
                    return result;
                }).orElse(new TreeSet<>());
    }
}
