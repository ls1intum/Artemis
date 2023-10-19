package de.tum.in.www1.artemis.service.dto.iris;

import java.util.Set;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;

public class IrisCombinedChatSubSettingsDTO {

    private boolean enabled;

    private Integer rateLimit;

    private Integer rateLimitTimeframeHours;

    private Set<String> allowedModels;

    private String preferredModel;

    private IrisTemplate template;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(Integer rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Integer getRateLimitTimeframeHours() {
        return rateLimitTimeframeHours;
    }

    public void setRateLimitTimeframeHours(Integer rateLimitTimeframeHours) {
        this.rateLimitTimeframeHours = rateLimitTimeframeHours;
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
