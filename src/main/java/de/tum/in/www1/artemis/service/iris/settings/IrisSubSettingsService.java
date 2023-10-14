package de.tum.in.www1.artemis.service.iris.settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.domain.iris.settings.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.dto.iris.IrisCombinedChatSubSettingsDTO;
import de.tum.in.www1.artemis.service.dto.iris.IrisCombinedCodeEditorSubSettingsDTO;
import de.tum.in.www1.artemis.service.dto.iris.IrisCombinedHestiaSubSettingsDTO;

/**
 * Service for handling {@link IrisSettings} objects.
 */
@Service
public class IrisSubSettingsService {

    private final AuthorizationCheckService authCheckService;

    public IrisSubSettingsService(AuthorizationCheckService authCheckService) {
        this.authCheckService = authCheckService;
    }

    public IrisChatSubSettings update(IrisChatSubSettings currentSettings, IrisChatSubSettings newSettings, IrisCombinedChatSubSettingsDTO parentSettings) {
        if (currentSettings == null) {
            currentSettings = new IrisChatSubSettings();
        }
        currentSettings.setEnabled(newSettings.isEnabled());
        currentSettings.setRateLimit(newSettings.getRateLimit());
        currentSettings.setAllowedModels(selectAllowedModels(currentSettings.getAllowedModels(), newSettings.getAllowedModels()));
        currentSettings.setPreferredModel(validatePreferredModel(currentSettings.getPreferredModel(), newSettings.getPreferredModel(), currentSettings.getAllowedModels(),
                parentSettings != null ? parentSettings.getAllowedModels() : null));
        currentSettings.setTemplate(newSettings.getTemplate());
        return currentSettings;
    }

    public IrisHestiaSubSettings update(IrisHestiaSubSettings currentSettings, IrisHestiaSubSettings newSettings, IrisCombinedHestiaSubSettingsDTO parentSettings) {
        if (currentSettings == null) {
            currentSettings = new IrisHestiaSubSettings();
        }
        currentSettings.setEnabled(newSettings.isEnabled());
        currentSettings.setAllowedModels(selectAllowedModels(currentSettings.getAllowedModels(), newSettings.getAllowedModels()));
        currentSettings.setPreferredModel(validatePreferredModel(currentSettings.getPreferredModel(), newSettings.getPreferredModel(), currentSettings.getAllowedModels(),
                parentSettings != null ? parentSettings.getAllowedModels() : null));
        currentSettings.setTemplate(newSettings.getTemplate());
        return currentSettings;
    }

    public IrisCodeEditorSubSettings update(IrisCodeEditorSubSettings currentSettings, IrisCodeEditorSubSettings newSettings, IrisCombinedCodeEditorSubSettingsDTO parentSettings) {
        if (currentSettings == null) {
            currentSettings = new IrisCodeEditorSubSettings();
        }
        currentSettings.setEnabled(newSettings.isEnabled());
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
        else if (allowedModels != null && allowedModels.contains(newPreferredModel)) {
            return newPreferredModel;
        }
        else if (parentAllowedModels != null && parentAllowedModels.contains(newPreferredModel)) {
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
            combinedChatSettings.setPreferredModel(getCombinedPreferredModel(settingsList, IrisSettings::getIrisChatSettings));
            combinedChatSettings.setTemplate(getCombinedTemplate(settingsList, IrisSettings::getIrisChatSettings));
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
        var combinedHestiaSettings = new IrisCombinedHestiaSubSettingsDTO();
        combinedHestiaSettings.setEnabled(getCombinedEnabled(settingsList, IrisSettings::getIrisHestiaSettings));
        combinedHestiaSettings.setPreferredModel(getCombinedPreferredModel(settingsList, IrisSettings::getIrisHestiaSettings));
        if (!minimal) {
            combinedHestiaSettings.setTemplate(getCombinedTemplate(settingsList, IrisSettings::getIrisHestiaSettings));
        }
        return combinedHestiaSettings;
    }

    /**
     * Combines the enabled field of multiple {@link IrisSettings} objects.
     * Simply &&s all enabled fields together.
     *
     * @param settingsList        List of {@link IrisSettings} objects to combine.
     * @param subSettingsFunction Function to get the sub settings from an IrisSettings object.
     * @return Combined enabled field.
     */
    private boolean getCombinedEnabled(ArrayList<IrisSettings> settingsList, Function<IrisSettings, IrisSubSettings> subSettingsFunction) {
        return settingsList.stream().map(subSettingsFunction).allMatch(IrisSubSettings::isEnabled);
    }

    /**
     * Combines the rateLimit field of multiple {@link IrisSettings} objects.
     * Simply takes the minimum rateLimit.
     *
     * @param settingsList List of {@link IrisSettings} objects to combine.
     * @return Combined rateLimit field.
     */
    private Integer getCombinedRateLimit(ArrayList<IrisSettings> settingsList) {
        return settingsList.stream().map(IrisSettings::getIrisChatSettings).map(IrisChatSubSettings::getRateLimit).filter(rateLimit -> rateLimit != null && rateLimit >= 0)
                .min(Comparator.comparingInt(Integer::intValue)).orElse(null);
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
    private String getCombinedPreferredModel(ArrayList<IrisSettings> settingsList, Function<IrisSettings, IrisSubSettings> subSettingsFunction) {
        return settingsList.stream().map(subSettingsFunction).map(IrisSubSettings::getPreferredModel).filter(model -> model != null && !model.isBlank())
                .reduce((first, second) -> second).orElseThrow();
    }

    /**
     * Combines the template field of multiple {@link IrisSettings} objects.
     * Simply takes the last template.
     *
     * @param settingsList        List of {@link IrisSettings} objects to combine.
     * @param subSettingsFunction Function to get the sub settings from an IrisSettings object.
     * @return Combined template field.
     */
    private IrisTemplate getCombinedTemplate(ArrayList<IrisSettings> settingsList, Function<IrisSettings, IrisSubSettings> subSettingsFunction) {
        return settingsList.stream().map(subSettingsFunction).map(IrisSubSettings::getTemplate).filter(template -> template != null && !template.getContent().isBlank())
                .reduce((first, second) -> second).orElseThrow();
    }
}
