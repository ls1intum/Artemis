package de.tum.cit.aet.artemis.sandbox.domain;

import java.util.List;

/**
 * The Security Framework state of a tests repository, as Ares would apply it when the tests run.
 *
 * @param status              the settled state
 * @param disabledTestClasses the test classes whose {@code @Policy} annotation is switched off, sorted
 * @param policyFiles         the policy files that an active {@code @Policy} annotation points to, sorted by path
 * @param unsupportedReasons  why the repository cannot be managed automatically; empty if it can
 * @param aresApiUses         the parts of Ares the tests use besides the sandbox, by file, sorted; removing the Ares library would break these files
 */
public record SandboxState(SandboxStatus status, List<String> disabledTestClasses, List<PolicyFile> policyFiles, List<UnsupportedReason> unsupportedReasons,
        List<AresApiUse> aresApiUses) {
}
