package de.tum.cit.aet.artemis.sandbox.domain;

/**
 * A reason why the Security Framework cannot manage a repository.
 *
 * @param location the affected file, class or build file
 * @param type     the reason
 */
public record UnsupportedReason(String location, UnsupportedReasonType type) {
}
