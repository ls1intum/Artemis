package de.tum.in.www1.artemis.service.dto.iris;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisCombinedCodeEditorSubSettingsDTO implements IrisCombinedSubSettingsInterface {

    private boolean enabled;

    private Set<String> allowedModels;

    private String preferredModel;

    private IrisTemplate chatTemplate;

    private IrisTemplate problemStatementGenerationTemplate;

    private IrisTemplate templateRepoGenerationTemplate;

    private IrisTemplate solutionRepoGenerationTemplate;

    private IrisTemplate testRepoGenerationTemplate;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getAllowedModels() {
        return allowedModels;
    }

    public void setAllowedModels(Set<String> allowedModels) {
        this.allowedModels = allowedModels;
    }

    public String getPreferredModel() {
        return preferredModel;
    }

    public void setPreferredModel(String preferredModel) {
        this.preferredModel = preferredModel;
    }

    public IrisTemplate getChatTemplate() {
        return chatTemplate;
    }

    public void setChatTemplate(IrisTemplate chatTemplate) {
        this.chatTemplate = chatTemplate;
    }

    public IrisTemplate getProblemStatementGenerationTemplate() {
        return problemStatementGenerationTemplate;
    }

    public void setProblemStatementGenerationTemplate(IrisTemplate problemStatementGenerationTemplate) {
        this.problemStatementGenerationTemplate = problemStatementGenerationTemplate;
    }

    public IrisTemplate getTemplateRepoGenerationTemplate() {
        return templateRepoGenerationTemplate;
    }

    public void setTemplateRepoGenerationTemplate(IrisTemplate templateRepoGenerationTemplate) {
        this.templateRepoGenerationTemplate = templateRepoGenerationTemplate;
    }

    public IrisTemplate getSolutionRepoGenerationTemplate() {
        return solutionRepoGenerationTemplate;
    }

    public void setSolutionRepoGenerationTemplate(IrisTemplate solutionRepoGenerationTemplate) {
        this.solutionRepoGenerationTemplate = solutionRepoGenerationTemplate;
    }

    public IrisTemplate getTestRepoGenerationTemplate() {
        return testRepoGenerationTemplate;
    }

    public void setTestRepoGenerationTemplate(IrisTemplate testRepoGenerationTemplate) {
        this.testRepoGenerationTemplate = testRepoGenerationTemplate;
    }
}
