package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;

/**
 * Holds the SPEC.md content that PASSED the specification gate, per sandbox session.
 * <p>
 * Every later gate derives from the specification — which types the template must omit, whether the statement needs a diagram — but SPEC.md lives in the workspace and the agent
 * may write it, so reading the live file at gate time lets the agent dissolve the contract it is being judged against by downgrading its own ownership decisions until the
 * artifacts it has pass.
 * <p>
 * This snapshot is therefore unreachable from the sandbox and is every downstream gate's sole authority, while the guarded file tools reject edits and restore it after an
 * out-of-band shell mutation. When implementation exposes a conflict, the executable artifacts are what must be restructured.
 */
@Lazy
@Component
@Conditional(HyperionExerciseGenerationEnabled.class)
public class ApprovedSpecRegistry {

    private final Map<String, String> approvedBySession = new ConcurrentHashMap<>();

    /**
     * Records the specification a session's spec gate approved. Approval is immutable: repeating the same value is harmless, a different second value is a lifecycle bug.
     *
     * @param sessionId the sandbox session the specification belongs to
     * @param spec      the approved SPEC.md content
     */
    public void approve(String sessionId, String spec) {
        if (sessionId != null && spec != null && !spec.isBlank()) {
            String existing = approvedBySession.putIfAbsent(sessionId, spec);
            if (existing != null && !existing.equals(spec)) {
                throw new IllegalStateException("A different specification is already approved for sandbox session " + sessionId);
            }
        }
    }

    /**
     * @param sessionId the sandbox session
     * @return the specification this session's spec gate approved, or empty when the spec stage never ran (the instructor's statement IS the specification, an ADAPT run, or a
     *         non-staged language)
     */
    public Optional<String> approved(String sessionId) {
        return sessionId == null ? Optional.empty() : Optional.ofNullable(approvedBySession.get(sessionId));
    }

    /**
     * Drops a finished session's specification, so the registry never outlives the runs it describes.
     *
     * @param sessionId the sandbox session being destroyed
     */
    public void forget(String sessionId) {
        if (sessionId != null) {
            approvedBySession.remove(sessionId);
        }
    }
}
