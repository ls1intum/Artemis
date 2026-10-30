package de.tum.cit.aet.artemis.programming.domain.security;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The Security Framework state of a tests repository, as Ares would apply it when the tests run.
 *
 * @param status              the settled state
 * @param disabledTestClasses the test classes whose {@code @Policy} annotation is switched off, sorted
 * @param policyFiles         the policy files that an active {@code @Policy} annotation points to, sorted by path
 * @param unsupportedReasons  why the repository cannot be managed automatically; empty if it can
 */
public record SecurityFrameworkState(SecurityActivationStatus status, List<String> disabledTestClasses, List<PolicyFile> policyFiles, List<UnsupportedReason> unsupportedReasons) {

    /**
     * A policy file that an active {@code @Policy} annotation points to.
     *
     * @param policyPath         the path relative to the repository root, as the annotations name it
     * @param referencedBy       the test classes whose active annotation points to it, sorted
     * @param problem            why Ares cannot use the file or why it cannot be checked; absent if it is fine
     * @param missingTestClasses the test classes that point to the file but are not listed in its {@code theFollowingClassesAreTestClasses}, so Ares treats
     *                               them like student code; only determined if the file can be read
     */
    public record PolicyFile(String policyPath, List<String> referencedBy, @Nullable PolicyFileProblem problem, List<String> missingTestClasses) {
    }

    /**
     * Why a policy file cannot be used or checked.
     */
    public enum PolicyFileProblem {

        /** The path leaves the repository, is absolute, points into {@code .git} or to a symbolic link or directory. */
        INVALID_PATH,
        /** The file does not exist, so Ares fails the tests with {@code security.policy.reader.path.not.exists}. */
        MISSING,
        /** The file is larger than the Security Framework reads. */
        TOO_LARGE,
        /** The file is not a valid policy. */
        UNPARSEABLE,
        /** The file is valid, but its list of test classes cannot be located, so it is neither checked nor updated. */
        TEST_CLASS_LIST_NOT_FOUND;

        /**
         * @return whether Ares fails the tests or the state cannot be determined because of this problem
         */
        public boolean breaksTheSandbox() {
            return this != TEST_CLASS_LIST_NOT_FOUND;
        }
    }

    /**
     * A reason why the Security Framework cannot manage a repository.
     *
     * @param location the affected file, class or build file
     * @param type     the reason
     */
    public record UnsupportedReason(String location, UnsupportedReasonType type) {
    }

    /**
     * The kinds of repositories the Security Framework does not manage, because changing them could not be done reliably.
     */
    public enum UnsupportedReasonType {
        /** A {@code @Policy} argument is an expression instead of a literal. */
        UNEVALUABLE_ARGUMENTS,
        /** A {@code @Policy} annotation is on a method, a field or a nested type. */
        POLICY_NOT_ON_TOP_LEVEL_TYPE,
        /** A {@code @Policy} annotation is on an annotation type, i.e. a meta-annotation. */
        POLICY_ON_ANNOTATION_TYPE,
        /** A file declares more than one top-level type that is a test class or carries a {@code @Policy}. */
        MULTIPLE_TOP_LEVEL_TYPES,
        /** Tests or Ares markers are inside a nested type. */
        TESTS_IN_NESTED_TYPE,
        /** A test class registers the Ares sandbox but has no {@code @Policy} of its own, so it inherits one or uses the strict default. */
        TEST_CLASS_WITHOUT_POLICY,
        /** A test class does not register the Ares sandbox through {@code @Public}, {@code @Hidden}, {@code @PublicTest} or {@code @HiddenTest}. */
        TEST_CLASS_WITHOUT_ARES,
        /** The tests use Ares 1, whose own sandbox the Security Framework does not control. */
        ARES_1,
        /** No test class uses Ares 2. */
        NO_ARES_2,
        /** The build file does not pin an Ares version through {@code aresVersion} or {@code ares.version}. */
        NO_ARES_VERSION_PINNED
    }
}
