package de.tum.cit.aet.artemis.service.iris.settings;

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

import de.tum.cit.aet.artemis.domain.iris.IrisTemplate;
import de.tum.cit.aet.artemis.domain.iris.settings.IrisChatSubSettings;
import de.tum.cit.aet.artemis.domain.iris.settings.IrisCompetencyGenerationSubSettings;
import de.tum.cit.aet.artemis.domain.iris.settings.IrisExerciseSettings;
import de.tum.cit.aet.artemis.domain.iris.settings.IrisHestiaSubSettings;
import de.tum.cit.aet.artemis.domain.iris.settings.IrisLectureIngestionSubSettings;
import de.tum.cit.aet.artemis.domain.iris.settings.IrisSettings;
import de.tum.cit.aet.artemis.domain.iris.settings.IrisSettingsType;
import de.tum.cit.aet.artemis.domain.iris.settings.IrisSubSettings;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.iris.dto.IrisCombinedChatSubSettingsDTO;
import de.tum.cit.aet.artemis.service.iris.dto.IrisCombinedCompetencyGenerationSubSettingsDTO;
import de.tum.cit.aet.artemis.service.iris.dto.IrisCombinedHestiaSubSettingsDTO;
import de.tum.cit.aet.artemis.service.iris.dto.IrisCombinedLectureIngestionSubSettingsDTO;

/**
 * Service for handling {@link IrisSubSettings} objects.
 * This server provides methods to update and combine sub settings objects.
 * See {@link IrisSettingsService} for more information about handling {@link IrisSettings}.
 */
@Service
@Profile("iris")
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
        if (authCheckService.isAdmin()) {
            currentSettings.setRateLimit(newSettings.getRateLimit());
            currentSettings.setRateLimitTimeframeHours(newSettings.getRateLimitTimeframeHours());
        }
        currentSettings.setAllowedModels(selectAllowedModels(currentSettings.getAllowedModels(), newSettings.getAllowedModels()));
        currentSettings.setPreferredModel(validatePreferredModel(currentSettings.getPreferredModel(), newSettings.getPreferredModel(), currentSettings.getAllowedModels(),
                parentSettings != null ? parentSettings.allowedModels() : null));
        currentSettings.setTemplate(newSettings.getTemplate());
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
     * Updates a Hestia sub settings object.
     * If the new settings are null, the current settings will be deleted (except if the parent settings are null == if the settings are global).
     * Special notes:
     * - If the user is not an admin the allowed models will not be updated.
     * - If the user is not an admin the preferred model will only be updated if it is included in the allowed models.
     *
     * @param currentSettings Current Hestia sub settings.
     * @param newSettings     Updated Hestia sub settings.
     * @param parentSettings  Parent Hestia sub settings.
     * @param settingsType    Type of the settings the sub settings belong to.
     * @return Updated Hestia sub settings.
     */
    public IrisHestiaSubSettings update(IrisHestiaSubSettings currentSettings, IrisHestiaSubSettings newSettings, IrisCombinedHestiaSubSettingsDTO parentSettings,
            IrisSettingsType settingsType) {
        if (newSettings == null) {
            if (parentSettings == null) {
                throw new IllegalArgumentException("Cannot delete the Hestia settings");
            }
            return null;
        }
        if (currentSettings == null) {
            currentSettings = new IrisHestiaSubSettings();
        }
        if (settingsType == IrisSettingsType.EXERCISE || authCheckService.isAdmin()) {
            currentSettings.setEnabled(newSettings.isEnabled());
        }
        currentSettings.setAllowedModels(selectAllowedModels(currentSettings.getAllowedModels(), newSettings.getAllowedModels()));
        currentSettings.setPreferredModel(validatePreferredModel(currentSettings.getPreferredModel(), newSettings.getPreferredModel(), currentSettings.getAllowedModels(),
                parentSettings != null ? parentSettings.allowedModels() : null));
        currentSettings.setTemplate(newSettings.getTemplate());
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
            currentSettings.setAllowedModels(selectAllowedModels(currentSettings.getAllowedModels(), newSettings.getAllowedModels()));
        }
        currentSettings.setPreferredModel(validatePreferredModel(currentSettings.getPreferredModel(), newSettings.getPreferredModel(), currentSettings.getAllowedModels(),
                parentSettings != null ? parentSettings.allowedModels() : null));
        currentSettings.setTemplate(newSettings.getTemplate());
        return currentSettings;
    }

    /**
     * Filters the allowed models of a sub settings object.
     * If the user is an admin, all models are allowed.
     * Otherwise, only models that are allowed by the parent settings or the current settings are allowed.
     *
     * @param allowedModels        The allowed models of the current settings.
     * @param updatedAllowedModels The allowed models of the updated settings.
     * @return The filtered allowed models.
     */
    private SortedSet<String> selectAllowedModels(SortedSet<String> allowedModels, SortedSet<String> updatedAllowedModels) {
        return authCheckService.isAdmin() ? updatedAllowedModels : allowedModels;
    }

    /**
     * Validates the preferred model of a sub settings object.
     * If the user is an admin, all models are allowed.
     * Otherwise, only models that are allowed by the current settings are allowed.
     *
     * @param preferredModel      The preferred model of the current settings.
     * @param newPreferredModel   The preferred model of the updated settings.
     * @param allowedModels       The allowed models of the current settings.
     * @param parentAllowedModels The allowed models of the parent settings.
     * @return The validated preferred model.
     */
    private String validatePreferredModel(String preferredModel, String newPreferredModel, Set<String> allowedModels, Set<String> parentAllowedModels) {
        if (newPreferredModel == null || newPreferredModel.isBlank()) {
            return null;
        }
        var canChangePreferredModel = authCheckService.isAdmin() || (allowedModels != null && !allowedModels.isEmpty() && allowedModels.contains(newPreferredModel))
                || ((allowedModels == null || allowedModels.isEmpty()) && parentAllowedModels != null && parentAllowedModels.contains(newPreferredModel));
        if (canChangePreferredModel) {
            return newPreferredModel;
        }

        return preferredModel;
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
        var allowedModels = minimal ? getCombinedAllowedModels(settingsList, IrisSettings::getIrisChatSettings) : null;
        var preferredModel = minimal ? getCombinedPreferredModel(settingsList, IrisSettings::getIrisChatSettings) : null;
        var template = minimal ? getCombinedTemplate(settingsList, IrisSettings::getIrisChatSettings, IrisChatSubSettings::getTemplate) : null;
        return new IrisCombinedChatSubSettingsDTO(enabled, rateLimit, null, allowedModels, preferredModel, template);
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
     * Combines the Hestia settings of multiple {@link IrisSettings} objects.
     * If minimal is true, the returned object will only contain the enabled field.
     * The minimal version can safely be sent to students.
     *
     * @param settingsList List of {@link IrisSettings} objects to combine.
     * @param minimal      Whether to return a minimal version of the combined settings.
     * @return Combined Hestia settings.
     */
    public IrisCombinedHestiaSubSettingsDTO combineHestiaSettings(ArrayList<IrisSettings> settingsList, boolean minimal) {
        var actualSettingsList = settingsList.stream().filter(settings -> !(settings instanceof IrisExerciseSettings)).toList();
        var enabled = getCombinedEnabled(actualSettingsList, IrisSettings::getIrisHestiaSettings);
        var allowedModels = minimal ? getCombinedAllowedModels(actualSettingsList, IrisSettings::getIrisHestiaSettings) : null;
        var preferredModel = minimal ? getCombinedPreferredModel(actualSettingsList, IrisSettings::getIrisHestiaSettings) : null;
        var template = minimal ? getCombinedTemplate(actualSettingsList, IrisSettings::getIrisHestiaSettings, IrisHestiaSubSettings::getTemplate) : null;
        return new IrisCombinedHestiaSubSettingsDTO(enabled, allowedModels, preferredModel, template);
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
        var allowedModels = minimal ? getCombinedAllowedModels(actualSettingsList, IrisSettings::getIrisCompetencyGenerationSettings) : null;
        var preferredModel = minimal ? getCombinedPreferredModel(actualSettingsList, IrisSettings::getIrisCompetencyGenerationSettings) : null;
        var template = minimal ? getCombinedTemplate(actualSettingsList, IrisSettings::getIrisCompetencyGenerationSettings, IrisCompetencyGenerationSubSettings::getTemplate)
                : null;
        return new IrisCombinedCompetencyGenerationSubSettingsDTO(enabled, allowedModels, preferredModel, template);
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
     * Combines the allowedModels field of multiple {@link IrisSettings} objects.
     * Simply takes the last allowedModels.
     *
     * @param settingsList        List of {@link IrisSettings} objects to combine.
     * @param subSettingsFunction Function to get the sub settings from an IrisSettings object.
     * @return Combined allowedModels field.
     */
    private Set<String> getCombinedAllowedModels(List<IrisSettings> settingsList, Function<IrisSettings, IrisSubSettings> subSettingsFunction) {
        return settingsList.stream().filter(Objects::nonNull).map(subSettingsFunction).filter(Objects::nonNull).map(IrisSubSettings::getAllowedModels).filter(Objects::nonNull)
                .filter(models -> !models.isEmpty()).reduce((first, second) -> second).orElse(new TreeSet<>());
    }

    /**
     * Combines the preferredModel field of multiple {@link IrisSettings} objects.
     * Simply takes the last preferredModel.
     * TODO
     *
     * @param settingsList        List of {@link IrisSettings} objects to combine.
     * @param subSettingsFunction Function to get the sub settings from an IrisSettings object.
     * @return Combined preferredModel field.
     */
    private String getCombinedPreferredModel(List<IrisSettings> settingsList, Function<IrisSettings, IrisSubSettings> subSettingsFunction) {
        return settingsList.stream().filter(Objects::nonNull).map(subSettingsFunction).filter(Objects::nonNull).map(IrisSubSettings::getPreferredModel)
                .filter(model -> model != null && !model.isBlank()).reduce((first, second) -> second).orElse(null);
    }

    /**
     * Combines the template field of multiple {@link IrisSettings} objects.
     * Simply takes the last template.
     *
     * @param settingsList     List of {@link IrisSettings} objects to combine.
     * @param templateFunction Function to get the template from the sub settings from an IrisSettings object.
     * @return Combined template field.
     */
    private <S extends IrisSubSettings> IrisTemplate getCombinedTemplate(List<IrisSettings> settingsList, Function<IrisSettings, S> subSettingsFunction,
            Function<S, IrisTemplate> templateFunction) {
        return settingsList.stream().filter(Objects::nonNull).map(subSettingsFunction).filter(Objects::nonNull).map(templateFunction)
                .filter(template -> template != null && template.getContent() != null && !template.getContent().isBlank()).reduce((first, second) -> second).orElse(null);
    }
}
