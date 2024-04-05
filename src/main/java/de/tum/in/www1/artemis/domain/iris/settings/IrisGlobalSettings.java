package de.tum.in.www1.artemis.domain.iris.settings;

import jakarta.persistence.*;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An {@link IrisSettings} implementation for global settings.
 * Global settings provide default values for all of Artemis for all sub setting types.
 * It also includes functionality to automatically update the sub settings in the future.
 */
@Entity
@DiscriminatorValue("GLOBAL")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisGlobalSettings extends IrisSettings {

    @Column(name = "current_version")
    private int currentVersion;

    @Column(name = "enable_auto_update_chat")
    private boolean enableAutoUpdateChat;

    @Column(name = "enable_auto_update_hestia")
    private boolean enableAutoUpdateHestia;

    @Column(name = "enable_auto_update_code_editor")
    private boolean enableAutoUpdateCodeEditor;

    @Column(name = "enable_auto_update_competency_generation")
    private boolean enableAutoUpdateCompetencyGeneration;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_chat_settings_id")
    private IrisChatSubSettings irisChatSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_hestia_settings_id")
    private IrisHestiaSubSettings irisHestiaSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_code_editor_settings_id")
    private IrisCodeEditorSubSettings irisCodeEditorSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "iris_competency_generation_settings_id")
    private IrisCompetencyGenerationSubSettings irisCompetencyGenerationSettings;

    @Override
    public boolean isValid() {
        var chatSettingsValid = !Hibernate.isInitialized(irisChatSettings) || irisChatSettings == null
                || (irisChatSettings.getTemplate() != null && irisChatSettings.getTemplate().getContent() != null && !irisChatSettings.getTemplate().getContent().isEmpty());
        var hestiaSettingsValid = !Hibernate.isInitialized(irisHestiaSettings) || irisHestiaSettings == null
                || (irisHestiaSettings.getTemplate() != null && irisHestiaSettings.getTemplate().getContent() != null && !irisHestiaSettings.getTemplate().getContent().isEmpty());
        var competencyGenerationSettingsValid = !Hibernate.isInitialized(irisCompetencyGenerationSettings) || irisCompetencyGenerationSettings == null
                || (irisCompetencyGenerationSettings.getTemplate() != null && irisCompetencyGenerationSettings.getTemplate().getContent() != null
                        && !irisCompetencyGenerationSettings.getTemplate().getContent().isEmpty());
        return chatSettingsValid && hestiaSettingsValid && competencyGenerationSettingsValid;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public boolean isEnableAutoUpdateChat() {
        return enableAutoUpdateChat;
    }

    public void setEnableAutoUpdateChat(boolean enableAutoUpdateChat) {
        this.enableAutoUpdateChat = enableAutoUpdateChat;
    }

    public boolean isEnableAutoUpdateHestia() {
        return enableAutoUpdateHestia;
    }

    public void setEnableAutoUpdateHestia(boolean enableAutoUpdateHestia) {
        this.enableAutoUpdateHestia = enableAutoUpdateHestia;
    }

    public boolean isEnableAutoUpdateCodeEditor() {
        return enableAutoUpdateCodeEditor;
    }

    public void setEnableAutoUpdateCodeEditor(boolean enableAutoUpdateCodeEditor) {
        this.enableAutoUpdateCodeEditor = enableAutoUpdateCodeEditor;
    }

    public boolean isEnableAutoUpdateCompetencyGeneration() {
        return enableAutoUpdateCompetencyGeneration;
    }

    public void setEnableAutoUpdateCompetencyGeneration(boolean enableAutoUpdateCompetencyGeneration) {
        this.enableAutoUpdateCompetencyGeneration = enableAutoUpdateCompetencyGeneration;
    }

    @Override
    public IrisChatSubSettings getIrisChatSettings() {
        return irisChatSettings;
    }

    @Override
    public void setIrisChatSettings(IrisChatSubSettings irisChatSettings) {
        this.irisChatSettings = irisChatSettings;
    }

    @Override
    public IrisHestiaSubSettings getIrisHestiaSettings() {
        return irisHestiaSettings;
    }

    @Override
    public void setIrisHestiaSettings(IrisHestiaSubSettings irisHestiaSettings) {
        this.irisHestiaSettings = irisHestiaSettings;
    }

    @Override
    public IrisCodeEditorSubSettings getIrisCodeEditorSettings() {
        return irisCodeEditorSettings;
    }

    @Override
    public void setIrisCodeEditorSettings(IrisCodeEditorSubSettings irisCodeEditorSettings) {
        this.irisCodeEditorSettings = irisCodeEditorSettings;
    }

    @Override
    public IrisCompetencyGenerationSubSettings getIrisCompetencyGenerationSettings() {
        return irisCompetencyGenerationSettings;
    }

    @Override
    public void setIrisCompetencyGenerationSettings(IrisCompetencyGenerationSubSettings irisCompetencyGenerationSettings) {
        this.irisCompetencyGenerationSettings = irisCompetencyGenerationSettings;
    }
}
