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
 * Every later gate is derived from the specification — which types the template must omit, whether the statement needs a diagram. But SPEC.md lives in the workspace and the
 * agent may write it, so reading the live file at gate time let the agent dissolve the contract it was being judged against: observed live, the template stage hit a compile
 * failure caused by its own early-written tests, edited SPEC.md to downgrade three {@code student-creates} types to {@code stubbed}, and the gate then dutifully enforced the
 * weaker contract.
 * <p>
 * Keeping the approved copy here makes the complete contract unreachable from the sandbox: every downstream gate uses this snapshot as its sole authority, while guarded file
 * tools reject edits and restore it after an out-of-band shell mutation. If implementation exposes a conflict, executable artifacts must be restructured to honour the accepted
 * learning contract rather than editing that contract until weaker artifacts pass.
 */
@Lazy
@Component
@Conditional(HyperionExerciseGenerationEnabled.class)
public class ApprovedSpecRegistry {

    private final Map<String, String> approvedBySession = new ConcurrentHashMap<>();

    /**
     * Records the specification a session's spec gate approved. Approval is immutable: repeating the same value is harmless, while a different second value is a lifecycle bug.
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
     * @return the specification this session's spec gate approved, or empty when the spec stage never ran (an instructor statement IS the specification, an ADAPT run, or a
     *         non-staged language)
     */
    public Optional<String> approved(String sessionId) {
        return sessionId == null ? Optional.empty() : Optional.ofNullable(approvedBySession.get(sessionId));
    }

    /**
     * Drops a finished session's specification. Called when the orchestrator destroys the sandbox session, so the registry never outlives the runs it describes.
     *
     * @param sessionId the sandbox session being destroyed
     */
    public void forget(String sessionId) {
        if (sessionId != null) {
            approvedBySession.remove(sessionId);
        }
    }
}
