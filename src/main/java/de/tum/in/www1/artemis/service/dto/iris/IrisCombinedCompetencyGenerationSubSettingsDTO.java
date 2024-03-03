package de.tum.in.www1.artemis.service.dto.iris;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisCombinedCompetencyGenerationSubSettingsDTO implements IrisCombinedSubSettingsInterface {

    private boolean enabled;

    private Set<String> allowedModels;

    private String preferredModel;

    private IrisTemplate template;

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

    public IrisTemplate getTemplate() {
        return template;
    }

    public void setTemplate(IrisTemplate template) {
        this.template = template;
    }

}
