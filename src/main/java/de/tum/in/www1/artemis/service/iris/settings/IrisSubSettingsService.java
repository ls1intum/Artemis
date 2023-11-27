package de.tum.in.www1.artemis.service.iris.settings;

import java.util.*;
import java.util.function.Function;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.domain.iris.settings.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.dto.iris.IrisCombinedChatSubSettingsDTO;
import de.tum.in.www1.artemis.service.dto.iris.IrisCombinedCodeEditorSubSettingsDTO;
import de.tum.in.www1.artemis.service.dto.iris.IrisCombinedHestiaSubSettingsDTO;

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
                parentSettings != null ? parentSettings.getAllowedModels() : null));
        currentSettings.setTemplate(newSettings.getTemplate());
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
                parentSettings != null ? parentSettings.getAllowedModels() : null));
        currentSettings.setTemplate(newSettings.getTemplate());
        return currentSettings;
    }

    /**
     * Updates a Code Editor sub settings object.
     * If the new settings are null, the current settings will be deleted (except if the parent settings are null == if the settings are global).
     * Special notes:
     * - If the user is not an admin the allowed models will not be updated.
     * - If the user is not an admin the preferred model will only be updated if it is included in the allowed models.
     *
     * @param currentSettings Current Code Editor sub settings.
     * @param newSettings     Updated Code Editor sub settings.
     * @param parentSettings  Parent Code Editor sub settings.
     * @param settingsType    Type of the settings the sub settings belong to.
     * @return Updated Code Editor sub settings.
     */
    public IrisCodeEditorSubSettings update(IrisCodeEditorSubSettings currentSettings, IrisCodeEditorSubSettings newSettings, IrisCombinedCodeEditorSubSettingsDTO parentSettings,
            IrisSettingsType settingsType) {
        if (newSettings == null) {
            if (parentSettings == null) {
                throw new IllegalArgumentException("Cannot delete the Code Editor settings");
            }
            return null;
        }
        if (currentSettings == null) {
            currentSettings = new IrisCodeEditorSubSettings();
        }
        if (settingsType == IrisSettingsType.EXERCISE || authCheckService.isAdmin()) {
            currentSettings.setEnabled(newSettings.isEnabled());
        }
        currentSettings.setAllowedModels(selectAllowedModels(currentSettings.getAllowedModels(), newSettings.getAllowedModels()));
        currentSettings.setPreferredModel(validatePreferredModel(currentSettings.getPreferredModel(), newSettings.getPreferredModel(), currentSettings.getAllowedModels(),
                parentSettings != null ? parentSettings.getAllowedModels() : null));
        currentSettings.setChatTemplate(newSettings.getChatTemplate());
        currentSettings.setProblemStatementGenerationTemplate(newSettings.getProblemStatementGenerationTemplate());
        currentSettings.setTemplateRepoGenerationTemplate(newSettings.getTemplateRepoGenerationTemplate());
        currentSettings.setSolutionRepoGenerationTemplate(newSettings.getSolutionRepoGenerationTemplate());
        currentSettings.setTestRepoGenerationTemplate(newSettings.getTestRepoGenerationTemplate());
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
    private Set<String> selectAllowedModels(Set<String> allowedModels, Set<String> updatedAllowedModels) {
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
        else if (authCheckService.isAdmin()) {
            return newPreferredModel;
        }
        else if (allowedModels != null && !allowedModels.isEmpty() && allowedModels.contains(newPreferredModel)) {
            return newPreferredModel;
        }
        else if ((allowedModels == null || allowedModels.isEmpty()) && parentAllowedModels != null && parentAllowedModels.contains(newPreferredModel)) {
            return newPreferredModel;
        }
        else {
            return preferredModel;
        }
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
        var combinedChatSettings = new IrisCombinedChatSubSettingsDTO();
        combinedChatSettings.setEnabled(getCombinedEnabled(settingsList, IrisSettings::getIrisChatSettings));
        combinedChatSettings.setRateLimit(getCombinedRateLimit(settingsList));
        if (!minimal) {
            combinedChatSettings.setAllowedModels(getCombinedAllowedModels(settingsList, IrisSettings::getIrisChatSettings));
            combinedChatSettings.setPreferredModel(getCombinedPreferredModel(settingsList, IrisSettings::getIrisChatSettings));
            combinedChatSettings.setTemplate(getCombinedTemplate(settingsList, IrisSettings::getIrisChatSettings, IrisChatSubSettings::getTemplate));
        }
        return combinedChatSettings;
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
        var combinedHestiaSettings = new IrisCombinedHestiaSubSettingsDTO();
        combinedHestiaSettings.setEnabled(getCombinedEnabled(actualSettingsList, IrisSettings::getIrisHestiaSettings));
        if (!minimal) {
            combinedHestiaSettings.setAllowedModels(getCombinedAllowedModels(actualSettingsList, IrisSettings::getIrisHestiaSettings));
            combinedHestiaSettings.setPreferredModel(getCombinedPreferredModel(actualSettingsList, IrisSettings::getIrisHestiaSettings));
            combinedHestiaSettings.setTemplate(getCombinedTemplate(actualSettingsList, IrisSettings::getIrisHestiaSettings, IrisHestiaSubSettings::getTemplate));
        }
        return combinedHestiaSettings;
    }

    /**
     * Combines the Code Editor settings of multiple {@link IrisSettings} objects.
     * If minimal is true, the returned object will only contain the enabled field.
     * The minimal version can safely be sent to students.
     *
     * @param settingsList List of {@link IrisSettings} objects to combine.
     * @param minimal      Whether to return a minimal version of the combined settings.
     * @return Combined Code Editor settings.
     */
    public IrisCombinedCodeEditorSubSettingsDTO combineCodeEditorSettings(ArrayList<IrisSettings> settingsList, boolean minimal) {
        var actualSettingsList = settingsList.stream().filter(settings -> !(settings instanceof IrisExerciseSettings)).toList();
        var combinedCodeEditorSettings = new IrisCombinedCodeEditorSubSettingsDTO();
        combinedCodeEditorSettings.setEnabled(getCombinedEnabled(actualSettingsList, IrisSettings::getIrisCodeEditorSettings));
        if (!minimal) {
            combinedCodeEditorSettings.setAllowedModels(getCombinedAllowedModels(actualSettingsList, IrisSettings::getIrisCodeEditorSettings));
            combinedCodeEditorSettings.setPreferredModel(getCombinedPreferredModel(actualSettingsList, IrisSettings::getIrisCodeEditorSettings));

            combinedCodeEditorSettings
                    .setChatTemplate(getCombinedTemplate(actualSettingsList, IrisSettings::getIrisCodeEditorSettings, IrisCodeEditorSubSettings::getChatTemplate));
            combinedCodeEditorSettings.setProblemStatementGenerationTemplate(
                    getCombinedTemplate(actualSettingsList, IrisSettings::getIrisCodeEditorSettings, IrisCodeEditorSubSettings::getProblemStatementGenerationTemplate));
            combinedCodeEditorSettings.setTemplateRepoGenerationTemplate(
                    getCombinedTemplate(actualSettingsList, IrisSettings::getIrisCodeEditorSettings, IrisCodeEditorSubSettings::getTemplateRepoGenerationTemplate));
            combinedCodeEditorSettings.setSolutionRepoGenerationTemplate(
                    getCombinedTemplate(actualSettingsList, IrisSettings::getIrisCodeEditorSettings, IrisCodeEditorSubSettings::getSolutionRepoGenerationTemplate));
            combinedCodeEditorSettings.setTestRepoGenerationTemplate(
                    getCombinedTemplate(actualSettingsList, IrisSettings::getIrisCodeEditorSettings, IrisCodeEditorSubSettings::getTestRepoGenerationTemplate));
        }
        return combinedCodeEditorSettings;
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
