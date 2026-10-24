package de.tum.cit.aet.artemis.hyperion.runtime.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** The trusted Ares harness contract, checked before admission and again in the worker. */
public final class AresTestHarness {

    public static final List<String> PATHS = List.of("build.gradle", "gradle/AresReservedPackages.gradle", "SecurityPolicy.yaml");

    private static final Pattern ARES_2_DEPENDENCY = Pattern.compile("(?m)^\\s*testImplementation\\s+['\"]de\\.tum\\.cit\\.ase:ares:");

    private AresTestHarness() {
    }

    /**
     * Checks the three trusted harness files without changing them.
     *
     * @param testsFiles committed tests-repository text
     * @return rejection reasons, or an empty list for a supported harness
     */
    public static List<String> reasons(Map<String, String> testsFiles) {
        String gradle = testsFiles == null ? null : testsFiles.get("build.gradle");
        if (gradle == null) {
            return List.of("Java tests must keep the seeded Gradle harness file containing the Ares 2 dependency and trusted-package protections.");
        }
        String gradleWithoutComments = JavaSourceText.stripComments(gradle);
        List<String> reasons = new ArrayList<>();
        if (!ARES_2_DEPENDENCY.matcher(gradleWithoutComments).find() || gradleWithoutComments.contains("de.tum.in.ase:artemis-java-test-sandbox:")) {
            reasons.add("Java generation requires the current Ares 2 dependency (de.tum.cit.ase:ares) in tests/build.gradle; legacy Ares 1 harnesses are not supported.");
        }
        String reserved = testsFiles.getOrDefault("gradle/AresReservedPackages.gradle", "");
        if (!gradleWithoutComments.contains("apply from: 'gradle/AresReservedPackages.gradle'") || !reserved.contains("de/tum/cit/ase/ares/**")
                || !reserved.contains("org/junit/**")) {
            reasons.add("Java Ares 2 tests must keep the seeded AresReservedPackages.gradle guard and apply it from build.gradle.");
        }
        String policy = testsFiles.get("SecurityPolicy.yaml");
        if (policy == null || policy.isBlank()) {
            reasons.add("Ares 2 tests must keep the explicit SecurityPolicy.yaml; the default policy must not derive exemptions from student code.");
        }
        return reasons;
    }
}
