package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** Live, database-free admin view of an active Hyperion sandbox session. */
public record GenerationSandboxSessionDTO(String sessionId, String jobId, long exerciseId, String exerciseTitle, @Nullable Long courseId, String userLogin, String mode,
        Instant startedAt, Instant lastActivityAt) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public GenerationSandboxSessionDTO withSessionId(String newSessionId) {
        return new GenerationSandboxSessionDTO(newSessionId, jobId, exerciseId, exerciseTitle, courseId, userLogin, mode, startedAt, lastActivityAt);
    }
}
