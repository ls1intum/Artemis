package de.tum.cit.aet.artemis.sandbox.util;

/**
 * The {@code @Policy} annotation of the Java template tests, as it reads while the sandbox is active and after it was deactivated.
 */
public final class SecurityPolicyTestAnnotations {

    /** The annotation of the template tests, which points to the policy file at the root of the tests repository. */
    public static final String ACTIVE_ANNOTATION = "@de.tum.cit.ase.ares.api.Policy(value = \"" + TestRepositoryFiles.POLICY_FILE_NAME + "\")";

    /** How {@link #ACTIVE_ANNOTATION} reads after deactivation: only the activation flag changes, so reactivating restores the original annotation. */
    public static final String INACTIVE_ANNOTATION = "@de.tum.cit.ase.ares.api.Policy(value = \"" + TestRepositoryFiles.POLICY_FILE_NAME + "\", activated = false)";

    private SecurityPolicyTestAnnotations() {
    }
}
