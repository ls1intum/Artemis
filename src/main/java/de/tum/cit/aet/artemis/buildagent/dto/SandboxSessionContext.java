package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;

import org.jspecify.annotations.Nullable;

/** Parent generation metadata required to correlate a sandbox with its owning job in the admin UI. */
public record SandboxSessionContext(String jobId, long exerciseId, String exerciseTitle, @Nullable Long courseId, String userLogin, String mode) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
