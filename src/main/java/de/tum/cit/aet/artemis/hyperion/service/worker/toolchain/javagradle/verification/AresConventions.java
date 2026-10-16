package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Checks the current Ares harness and trusted annotations for Java generation. */
final class AresConventions {

    private static final Pattern ARES_2_DEPENDENCY = Pattern.compile("(?m)^\\s*testImplementation\\s+['\"]de\\.tum\\.cit\\.ase:ares:");

    private AresConventions() {
    }

    private static boolean isJavaTestSourcePath(String path) {
        return path.endsWith(".java") && (path.startsWith("test/") || path.startsWith("structural/test/") || path.startsWith("behavior/test/"));
    }

    private static String sampleNames(Set<String> names) {
        return names.stream().sorted().limit(5).collect(Collectors.joining(", "));
    }

    static List<String> harnessReasons(Map<String, String> testsFiles) {
        String gradle = testsFiles == null ? null : testsFiles.get("build.gradle");
        if (gradle == null) {
            return List.of("Java tests must keep the seeded Gradle harness file containing the Ares 2 dependency and trusted-package protections.");
        }
        String gradleWithoutComments = JavaSourceInspector.stripJavaComments(gradle);
        List<String> reasons = new ArrayList<>();
        if (!ARES_2_DEPENDENCY.matcher(gradleWithoutComments).find() || gradleWithoutComments.contains("de.tum.in.ase:artemis-java-test-sandbox:")) {
            reasons.add("Java generation requires the current Ares 2 dependency (de.tum.cit.ase:ares) in tests/build.gradle; legacy Ares 1 harnesses are not supported.");
        }
        String reserved = testsFiles.getOrDefault("gradle/AresReservedPackages.gradle", "");
        if (!gradleWithoutComments.contains("apply from: 'gradle/AresReservedPackages.gradle'") || !reserved.contains("de/tum/cit/ase/ares/**")
                || !reserved.contains("org/junit/**")) {
            reasons.add("Java Ares 2 tests must keep the seeded AresReservedPackages.gradle guard and apply it from build.gradle.");
        }
        String policy = testsFiles.get(AresSecurityPolicy.PATH);
        if (policy == null || policy.isBlank()) {
            reasons.add("Ares 2 tests must keep the explicit SecurityPolicy.yaml; the default policy must not derive exemptions from student code.");
        }
        return reasons;
    }

    static List<String> reasons(Map<String, String> seedTestsFiles, Map<String, String> producedTestsFiles, boolean preserveUnchangedTests) {
        if (producedTestsFiles == null || producedTestsFiles.isEmpty()) {
            return List.of();
        }
        Map<String, String> seed = seedTestsFiles == null ? Map.of() : seedTestsFiles;
        List<Map.Entry<String, String>> javaTests = producedTestsFiles.entrySet().stream().filter(entry -> isJavaTestSourcePath(entry.getKey())).toList();

        List<String> reasons = new ArrayList<>();
        List<String> generatedBuildOutput = producedTestsFiles.keySet().stream().filter(path -> path.startsWith("target/") || path.startsWith("build/")).toList();
        if (!generatedBuildOutput.isEmpty()) {
            reasons.add("Java tests repository must not contain generated build output such as target/ or build/ files; remove "
                    + sampleNames(new LinkedHashSet<>(generatedBuildOutput)) + ".");
        }
        reasons.addAll(harnessReasons(producedTestsFiles));
        if (producedTestsFiles.containsKey(AresSecurityPolicy.PATH)) {
            reasons.addAll(AresSecurityPolicy.reasons(seed, producedTestsFiles));
        }

        List<String> missingClassAnnotations = new ArrayList<>();
        List<String> missingTimeouts = new ArrayList<>();
        for (Map.Entry<String, String> javaTest : javaTests) {
            String path = javaTest.getKey();
            String content = javaTest.getValue();
            var annotationSummary = JavaSourceInspector.javaTestAnnotationSummary(content);
            // An unchanged annotation declaration can enable a newly written test elsewhere, so composition never inherits the adaptation exemption.
            if (annotationSummary.unsupportedAnnotationSyntax()) {
                reasons.add("Java test annotations cannot be inspected safely in " + path
                        + ". Apply JUnit annotations directly to test methods; composed annotations and malformed annotation syntax cannot establish Ares restrictions or bounded StrictTimeout.");
                continue;
            }
            if (preserveUnchangedTests && Objects.equals(seed.get(path), content)) {
                continue;
            }
            if (annotationSummary.hasTestMethods() && annotationSummary.classWithMissingAresAnnotations()) {
                missingClassAnnotations.add(path);
            }
            if (annotationSummary.testMethodWithoutStrictTimeout()) {
                missingTimeouts.add(path);
            }
        }
        if (!missingClassAnnotations.isEmpty()) {
            reasons.add("Java test classes must use trusted Ares 2 @Public and @Policy(value = \"SecurityPolicy.yaml\"); missing or shadowed in "
                    + sampleNames(new LinkedHashSet<>(missingClassAnnotations)));
        }
        if (!missingTimeouts.isEmpty()) {
            reasons.add("Every Java @Test method must carry the trusted de.tum.cit.ase.ares.api.StrictTimeout" + ", set to a bounded number of seconds between "
                    + JavaSourceInspector.MIN_STRICT_TIMEOUT_SECONDS + " and " + JavaSourceInspector.MAX_STRICT_TIMEOUT_SECONDS
                    + " inclusive (e.g. @StrictTimeout(1)), so an infinite loop cannot hang grading and a generous but still-bounded structural check is not falsely rejected; "
                    + "missing, shadowed, or out of that range in " + sampleNames(new LinkedHashSet<>(missingTimeouts)) + ".");
        }
        return reasons;
    }

}
