package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Checks the Ares harness and trusted annotations for legacy and current Java exercises. */
final class AresConventions {

    private static final Pattern ARES_SANDBOX_DEPENDENCY = Pattern.compile("(?m)^\\s*(?:testImplementation|implementation)\\s+['\"]de\\.tum\\.in\\.ase:artemis-java-test-sandbox:");

    private static final Pattern ARES_2_DEPENDENCY = Pattern.compile("(?m)^\\s*testImplementation\\s+['\"]de\\.tum\\.cit\\.ase:ares:");

    private static final Pattern FORBIDDEN_PACKAGE_FOLDERS = Pattern.compile("(?m)^\\s*def\\s+forbiddenPackageFolders\\s*=");

    private AresConventions() {
    }

    private static boolean isJavaTestSourcePath(String path) {
        return path.endsWith(".java") && (path.startsWith("test/") || path.startsWith("structural/test/") || path.startsWith("behavior/test/"));
    }

    private static String sampleNames(Set<String> names) {
        return names.stream().sorted().limit(5).collect(Collectors.joining(", "));
    }

    static List<String> reasons(Map<String, String> seedTestsFiles, Map<String, String> producedTestsFiles, boolean preserveUnchangedLegacyTests) {
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
        String gradle = producedTestsFiles.get("build.gradle");
        boolean ares2 = gradle != null && ARES_2_DEPENDENCY.matcher(JavaSourceInspector.stripJavaComments(gradle)).find();
        if (gradle != null) {
            String gradleWithoutComments = JavaSourceInspector.stripJavaComments(gradle);
            if (!ares2 && !ARES_SANDBOX_DEPENDENCY.matcher(gradleWithoutComments).find()) {
                reasons.add(
                        "Java Gradle tests must keep the Artemis Ares dependency in tests/build.gradle (de.tum.in.ase:artemis-java-test-sandbox); do not replace it with plain JUnit.");
            }
            if (ares2) {
                String reserved = producedTestsFiles.getOrDefault("gradle/AresReservedPackages.gradle", "");
                if (!gradleWithoutComments.contains("apply from: 'gradle/AresReservedPackages.gradle'") || !reserved.contains("de/tum/cit/ase/ares/**")
                        || !reserved.contains("org/junit/**")) {
                    reasons.add("Java Ares 2 tests must keep the seeded AresReservedPackages.gradle guard and apply it from build.gradle.");
                }
                reasons.addAll(AresSecurityPolicy.reasons(seed, producedTestsFiles));
            }
            else {
                if (!FORBIDDEN_PACKAGE_FOLDERS.matcher(gradleWithoutComments).find() || !gradleWithoutComments.contains("de/tum/in/test/api/")
                        || !gradleWithoutComments.contains("org/junit/")) {
                    reasons.add("Java Gradle tests must keep the seeded forbidden-package checks in tests/build.gradle so student code cannot shadow trusted packages.");
                }
            }
        }
        else {
            reasons.add("Java tests must keep the seeded Gradle harness file containing the Artemis Ares dependency and trusted-package protections.");
        }

        List<String> missingClassAnnotations = new ArrayList<>();
        List<String> missingTimeouts = new ArrayList<>();
        for (Map.Entry<String, String> javaTest : javaTests) {
            String path = javaTest.getKey();
            String content = javaTest.getValue();
            var annotationSummary = JavaSourceInspector.javaTestAnnotationSummary(content, ares2);
            // An unchanged annotation declaration can enable a newly written test elsewhere, so composition never inherits the legacy exemption.
            if (annotationSummary.unsupportedAnnotationSyntax()) {
                reasons.add("Java test annotations cannot be inspected safely in " + path
                        + ". Apply JUnit annotations directly to test methods; composed annotations and malformed annotation syntax cannot establish Ares restrictions or bounded StrictTimeout.");
                continue;
            }
            if (preserveUnchangedLegacyTests && Objects.equals(seed.get(path), content)) {
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
            reasons.add(ares2
                    ? "Java test classes must use trusted Ares 2 @Public and @Policy(value = \"SecurityPolicy.yaml\"); missing or shadowed in "
                            + sampleNames(new LinkedHashSet<>(missingClassAnnotations))
                    : "Java test classes must use the trusted Ares annotations @Public (de.tum.in.test.api.jupiter.Public), @WhitelistPath(\"build\") "
                            + "(de.tum.in.test.api.WhitelistPath), and @BlacklistPath(\"build/classes/java/test\") (de.tum.in.test.api.BlacklistPath); missing or shadowed in "
                            + sampleNames(new LinkedHashSet<>(missingClassAnnotations))
                            + ". Copy these exact imports from the seeded reference tests; only @Public lives in the .jupiter package.");
        }
        if (!missingTimeouts.isEmpty()) {
            reasons.add("Every Java @Test method must carry the trusted " + (ares2 ? "de.tum.cit.ase.ares.api.StrictTimeout" : "de.tum.in.test.api.StrictTimeout")
                    + ", set to a bounded number of seconds between " + JavaSourceInspector.MIN_STRICT_TIMEOUT_SECONDS + " and " + JavaSourceInspector.MAX_STRICT_TIMEOUT_SECONDS
                    + " inclusive (e.g. @StrictTimeout(1)), so an infinite loop cannot hang grading and a generous but still-bounded structural check is not falsely rejected; "
                    + "missing, shadowed, or out of that range in " + sampleNames(new LinkedHashSet<>(missingTimeouts)) + ".");
        }
        return reasons;
    }

}
