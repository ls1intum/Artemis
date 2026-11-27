package de.tum.cit.aet.artemis.sandbox.domain;

/** The result of checking a policy against a release. */
public enum CompatibilityVerdict {
    /** The release accepts every field and the format version of the policy. */
    COMPATIBLE,
    /** The release does not accept a field or the format version of the policy, so Ares would fail every test. */
    INCOMPATIBLE,
    /** The manifest does not list the release, or the policy cannot be read, so compatibility could not be checked. */
    UNVERIFIED
}
