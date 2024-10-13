package de.tum.cit.aet.artemis.iris.service.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisChatSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCompetencyGenerationSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisExerciseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisLectureIngestionSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSettingsType;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisTextExerciseChatSubSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedChatSubSettingsDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedCompetencyGenerationSubSettingsDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedLectureIngestionSubSettingsDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedTextExerciseChatSubSettingsDTO;

/**
 * Service for handling {@link IrisSubSettings} objects.
 * This server provides methods to update and combine sub settings objects.
 * See {@link IrisSettingsService} for more information about handling {@link IrisSettings}.
 */
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
     *
     * @param currentSettings Current chat sub settings.
     * @param newSettings     Updated chat sub settings.
     * @param parentSettings  Parent chat sub settings.
     * @param settingsType    Type of the settings the sub settings belong to.
     * @return Updated chat sub settings.
     */
    public IrisChatSubSettings update(IrisChatSubSettings currentSettings, IrisChatSubSettings newSettings, IrisCombinedChatSubSettingsDTO parentSettings,
            IrisSettingsType settingsType) {
        if (newSettings == null) {
            if (parentSettings == null) {
                throw new IllegalArgumentException("Cannot delete the chat settings");
            }
            return null;
        }
        if (currentSettings == null) {
            currentSettings = new IrisChatSubSettings();
        }
        if (settingsType == IrisSettingsType.EXERCISE || authCheckService.isAdmin()) {
            currentSettings.setEnabled(newSettings.isEnabled());
        }
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
        if (settingsType == IrisSettingsType.EXERCISE || authCheckService.isAdmin()) {
            currentSettings.setEnabled(newSettings.isEnabled());
        }
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
        return currentSettings;
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

        if (authCheckService.isAdmin() && (settingsType == IrisSettingsType.COURSE || settingsType == IrisSettingsType.GLOBAL)) {
            currentSettings.setEnabled(newSettings.isEnabled());
            currentSettings.setAutoIngestOnLectureAttachmentUpload(newSettings.getAutoIngestOnLectureAttachmentUpload());
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
        if (authCheckService.isAdmin()) {
            currentSettings.setEnabled(newSettings.isEnabled());
            currentSettings.setAllowedVariants(selectAllowedVariants(currentSettings.getAllowedVariants(), newSettings.getAllowedVariants()));
        }
        currentSettings.setSelectedVariant(validateSelectedVariant(currentSettings.getSelectedVariant(), newSettings.getSelectedVariant(), currentSettings.getAllowedVariants(),
                parentSettings != null ? parentSettings.allowedVariants() : null));
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
        var allowedVariants = !minimal ? getCombinedAllowedVariants(settingsList, IrisSettings::getIrisChatSettings) : null;
        var selectedVariant = !minimal ? getCombinedSelectedVariant(settingsList, IrisSettings::getIrisChatSettings) : null;
        var enabledForCategories = !minimal ? getCombinedEnabledForCategories(settingsList, IrisSettings::getIrisChatSettings) : null;
        return new IrisCombinedTextExerciseChatSubSettingsDTO(enabled, rateLimit, null, allowedVariants, selectedVariant, enabledForCategories);
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
    public IrisCombinedChatSubSettingsDTO combineChatSettings(ArrayList<IrisSettings> settingsList, boolean minimal) {
        var enabled = getCombinedEnabled(settingsList, IrisSettings::getIrisChatSettings);
        var rateLimit = getCombinedRateLimit(settingsList);
        var allowedVariants = !minimal ? getCombinedAllowedVariants(settingsList, IrisSettings::getIrisChatSettings) : null;
        var selectedVariant = !minimal ? getCombinedSelectedVariant(settingsList, IrisSettings::getIrisChatSettings) : null;
        var enabledForCategories = !minimal ? getCombinedEnabledForCategories(settingsList, IrisSettings::getIrisChatSettings) : null;
        return new IrisCombinedChatSubSettingsDTO(enabled, rateLimit, null, allowedVariants, selectedVariant, enabledForCategories);
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
        return new IrisCombinedLectureIngestionSubSettingsDTO(enabled);
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
        var selectedVariant = !minimal ? getCombinedSelectedVariant(actualSettingsList, IrisSettings::getIrisCompetencyGenerationSettings) : null;
        return new IrisCombinedCompetencyGenerationSubSettingsDTO(enabled, allowedVariants, selectedVariant);
    }

    /**
     * Combines the enabled field of multiple {@link IrisSettings} objects.
     * Simply &&s all enabled fields together.
     *
     * @param settingsList        List of {@link IrisSettings} objects to combine.
     * @param subSettingsFunction Function to get the sub settings from an IrisSettings object.
     * @return Combined enabled field.
     */
    private boolean getCombinedEnabled(List<IrisSettings> settingsList, Function<IrisSettings, IrisSubSettings> subSettingsFunction) {
        for (var irisSettings : settingsList) {
            if (irisSettings == null) {
                return false;
            }
            var settings = subSettingsFunction.apply(irisSettings);
            if (settings == null || !settings.isEnabled()) {
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
        return settingsList.stream().filter(Objects::nonNull).map(IrisSettings::getIrisChatSettings).filter(Objects::nonNull).map(IrisChatSubSettings::getRateLimit)
                .filter(rateLimit -> rateLimit != null && rateLimit >= 0).min(Comparator.comparingInt(Integer::intValue)).orElse(null);
    }

    /**
     * Combines the allowedVariants field of multiple {@link IrisSettings} objects.
     * Simply takes the last allowedVariants.
     *
     * @param settingsList        List of {@link IrisSettings} objects to combine.
     * @param subSettingsFunction Function to get the sub settings from an IrisSettings object.
     * @return Combined allowedVariants field.
     */
    private Set<String> getCombinedAllowedVariants(List<IrisSettings> settingsList, Function<IrisSettings, IrisSubSettings> subSettingsFunction) {
        return settingsList.stream().filter(Objects::nonNull).map(subSettingsFunction).filter(Objects::nonNull).map(IrisSubSettings::getAllowedVariants).filter(Objects::nonNull)
                .filter(models -> !models.isEmpty()).reduce((first, second) -> second).orElse(new TreeSet<>());
    }

    /**
     * Combines the selectedVariant field of multiple {@link IrisSettings} objects.
     * Simply takes the last selectedVariant.
     *
     * @param settingsList        List of {@link IrisSettings} objects to combine.
     * @param subSettingsFunction Function to get the sub settings from an IrisSettings object.
     * @return Combined selectedVariant field.
     */
    private String getCombinedSelectedVariant(List<IrisSettings> settingsList, Function<IrisSettings, IrisSubSettings> subSettingsFunction) {
        return settingsList.stream().filter(Objects::nonNull).map(subSettingsFunction).filter(Objects::nonNull).map(IrisSubSettings::getSelectedVariant)
                .filter(model -> model != null && !model.isBlank()).reduce((first, second) -> second).orElse(null);
    }

    private Set<String> getCombinedEnabledForCategories(List<IrisSettings> settingsList, Function<IrisSettings, IrisChatSubSettings> subSettingsFunction) {
        return settingsList.stream().filter(Objects::nonNull).filter(settings -> settings instanceof IrisCourseSettings).map(subSettingsFunction).filter(Objects::nonNull)
                .map(IrisChatSubSettings::getEnabledForCategories).filter(Objects::nonNull).filter(models -> !models.isEmpty()).reduce((first, second) -> second)
                .orElse(new TreeSet<>());
    }
}
