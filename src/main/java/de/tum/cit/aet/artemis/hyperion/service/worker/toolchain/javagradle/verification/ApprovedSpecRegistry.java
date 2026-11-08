package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.BriefLearningGoals;

/**
 * Retains brief-owned learning goals and the SPEC.md content that passed the specification gate, per sandbox session.
 * <p>
 * Every later gate derives from the specification — which types the template must omit, whether the statement needs a diagram — but SPEC.md lives in the workspace and the agent
 * may write it, so reading the live file at gate time would let the agent dissolve the contract it is judged against by downgrading its own ownership decisions until the
 * artifacts it already has pass. This snapshot is unreachable from the sandbox and is every downstream gate's sole authority. Executable assessment or ownership evidence can
 * authorize the staged controller to revoke its exact approval and repeat specification review within the existing budget; the agent cannot revoke it.
 */
public class ApprovedSpecRegistry {

    private record Approval(@Nullable String specification, boolean assessmentReopened, @Nullable BriefLearningGoals learningGoals) {
    }

    private final Map<String, Approval> approvedBySession = new ConcurrentHashMap<>();

    /**
     * Records the specification a session's spec gate approved. Approval stays immutable until the server explicitly reopens assessment review: repeating the same value is
     * harmless, a different value without revocation is a lifecycle bug.
     *
     * @param sessionId the sandbox session the specification belongs to
     * @param spec      the approved SPEC.md content
     */
    public void approve(String sessionId, String spec) {
        if (sessionId != null && spec != null && !spec.isBlank()) {
            approvedBySession.compute(sessionId, (key, existing) -> {
                if (existing != null && existing.specification() != null && !existing.specification().equals(spec)) {
                    throw new IllegalStateException("A different specification is already approved for sandbox session " + sessionId);
                }
                return new Approval(spec, existing != null && existing.assessmentReopened(), existing == null ? null : existing.learningGoals());
            });
        }
    }

    /**
     * @param sessionId the sandbox session
     * @return the specification this session's spec gate approved, or empty when the spec stage never ran (the instructor's statement IS the specification, an ADAPT run, or a
     *         non-staged language)
     */
    public Optional<String> approved(String sessionId) {
        Approval current = sessionId == null ? null : approvedBySession.get(sessionId);
        return current == null ? Optional.empty() : Optional.ofNullable(current.specification());
    }

    /**
     * Revokes only the exact contract disproved by executable assessment or ownership evidence, before a bounded server-controlled redesign.
     *
     * @param sessionId the sandbox session whose contract is being reviewed
     * @param expected  the exact approved specification that the counterexample challenged
     * @return whether that approval was revoked; false when a different contract is current or this session already used its redesign
     */
    public boolean reopenForAssessment(String sessionId, String expected) {
        Approval current = sessionId == null ? null : approvedBySession.get(sessionId);
        return current != null && !current.assessmentReopened() && expected != null && expected.equals(current.specification())
                && approvedBySession.replace(sessionId, current, new Approval(null, true, current.learningGoals()));
    }

    /**
     * Retains source-only goal identities independently of concept changes, specification revisions and assessment re-entry.
     *
     * @param sessionId the sandbox session
     * @param goals     the immutable brief-owned scope
     */
    public void rememberLearningGoals(String sessionId, BriefLearningGoals goals) {
        approvedBySession.compute(sessionId, (key, existing) -> {
            if (existing != null && existing.learningGoals() != null && !existing.learningGoals().equals(goals)) {
                throw new IllegalStateException("Different brief learning goals are already retained for sandbox session " + sessionId);
            }
            return new Approval(existing == null ? null : existing.specification(), existing != null && existing.assessmentReopened(), goals);
        });
    }

    /**
     * @param sessionId the sandbox session
     * @return the brief-owned scope, including while specification approval is reopened
     */
    public Optional<BriefLearningGoals> learningGoals(String sessionId) {
        Approval current = sessionId == null ? null : approvedBySession.get(sessionId);
        return current == null ? Optional.empty() : Optional.ofNullable(current.learningGoals());
    }

    /**
     * @param sessionId the sandbox session
     * @return whether this session has already consumed its server-owned assessment redesign, across all authoring attempts
     */
    public boolean assessmentReopened(String sessionId) {
        Approval current = sessionId == null ? null : approvedBySession.get(sessionId);
        return current != null && current.assessmentReopened();
    }

    public void forget(String sessionId) {
        if (sessionId != null) {
            approvedBySession.remove(sessionId);
        }
    }
}
