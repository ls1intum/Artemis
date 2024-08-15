package de.tum.in.www1.artemis.service.iris.dto;

import java.util.Set;

import org.jspecify.annotations.Nullable;

public interface IrisCombinedSubSettingsInterface {

    boolean enabled();

    @Nullable
    Set<String> allowedModels();

    @Nullable
    String preferredModel();
}
