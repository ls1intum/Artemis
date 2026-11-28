package de.tum.cit.aet.artemis.sandbox.domain;

import java.util.List;

import org.jspecify.annotations.Nullable;

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
