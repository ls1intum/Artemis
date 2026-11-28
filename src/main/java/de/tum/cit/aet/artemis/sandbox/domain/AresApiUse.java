package de.tum.cit.aet.artemis.sandbox.domain;

/**
 * A part of Ares a test file uses besides the sandbox, e.g. a structural test provider, {@code @StrictTimeout} or {@code ReflectionTestUtils}. Decision B4
 * of Ares2 issue #386 removes the Ares library when the sandbox is switched off, which only works for tests that use none of these.
 *
 * @param location the file, relative to the repository root
 * @param api      the type or wildcard-imported package, relative to the Ares package, e.g. {@code StrictTimeout} or {@code structural.*}
 */
public record AresApiUse(String location, String api) {
}
