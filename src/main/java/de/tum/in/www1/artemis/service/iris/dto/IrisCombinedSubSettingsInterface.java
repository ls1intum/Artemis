package de.tum.in.www1.artemis.service.iris.dto;

import java.util.Set;

public interface IrisCombinedSubSettingsInterface {

    boolean isEnabled();

    void setEnabled(boolean enabled);

    Set<String> getAllowedModels();

    void setAllowedModels(Set<String> allowedModels);

    String getPreferredModel();

    void setPreferredModel(String preferredModel);
}
