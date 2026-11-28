package de.tum.cit.aet.artemis.sandbox.domain;

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
    NO_ARES_VERSION_PINNED,
    /** A Maven profile pins the Ares version, so the version the build uses depends on which profiles are active. */
    ARES_VERSION_IN_MAVEN_PROFILE
}
