package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the SPEC.md content that PASSED the specification gate, per sandbox session.
 * <p>
 * Every later gate derives from the specification — which types the template must omit, whether the statement needs a diagram — but SPEC.md lives in the workspace and the agent
 * may write it, so reading the live file at gate time would let the agent dissolve the contract it is judged against by downgrading its own ownership decisions until the
 * artifacts it already has pass. This snapshot is unreachable from the sandbox and is every downstream gate's sole authority; when implementation exposes a conflict, the
 * executable artifacts are what must be restructured.
 */
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
     * Replaces the approved specification with a server-derived ownership correction. Narrower than a second approval: the caller must name the exact text it corrects, so a
     * stale or concurrent amendment is refused rather than overwriting a newer contract. Only the server calls this; the agent can never reach the registry.
     *
     * @param sessionId the sandbox session the specification belongs to
     * @param expected  the currently approved SPEC.md content the correction was derived from
     * @param amended   the corrected SPEC.md content
     * @return whether the correction was recorded
     */
    public boolean amend(String sessionId, String expected, String amended) {
        if (sessionId == null || expected == null || amended == null || amended.isBlank()) {
            return false;
        }
        return approvedBySession.replace(sessionId, expected, amended);
    }

    /**
     * @param sessionId the sandbox session
     * @return the specification this session's spec gate approved, or empty when the spec stage never ran (the instructor's statement IS the specification, an ADAPT run, or a
     *         non-staged language)
     */
    public Optional<String> approved(String sessionId) {
        return sessionId == null ? Optional.empty() : Optional.ofNullable(approvedBySession.get(sessionId));
    }

    public void forget(String sessionId) {
        if (sessionId != null) {
            approvedBySession.remove(sessionId);
        }
    }
}
