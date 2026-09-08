package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * Sandbox image and owning-generation context. Resource limits come from the build agent, not the caller.
 * Only {@code runConfig.network} is used: absent inherits operator policy, {@code none} disables networking, and other values are rejected.
 */
public record SandboxSessionSpecDTO(String image, DockerRunConfig runConfig, SandboxSessionContextDTO context) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public SandboxSessionSpecDTO(String image, DockerRunConfig runConfig) {
        this(image, runConfig, null);
    }
}
