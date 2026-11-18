package de.tum.cit.aet.artemis.hyperion.runtime.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationBuildTool;

/** The trusted Ares harness contract of either Java build tool, checked before admission and again in the worker. */
public final class AresTestHarness {

    /** Every harness file of both build tools; a file the exercise's tool does not use is simply absent. */
    public static final List<String> PATHS = List.of("build.gradle", "gradle/AresReservedPackages.gradle", "pom.xml", "SecurityPolicy.yaml");

    private static final Pattern ARES_2_DEPENDENCY = Pattern.compile("(?m)^\\s*testImplementation\\s+['\"]de\\.tum\\.cit\\.ase:ares:");

    private static final Pattern ARES_2_MAVEN_DEPENDENCY = Pattern.compile("<groupId>\\s*de\\.tum\\.cit\\.ase\\s*</groupId>\\s*<artifactId>\\s*ares\\s*</artifactId>");

    private static final Pattern XML_COMMENT = Pattern.compile("(?s)<!--.*?-->");

    private static final String LEGACY_ARES_1 = "artemis-java-test-sandbox";

    private AresTestHarness() {
    }

    /**
     * Checks the trusted harness files of the exercise's build tool without changing them.
     *
     * @param buildTool  the build tool selected by the exercise's project type
     * @param testsFiles committed tests-repository text
     * @return rejection reasons, or an empty list for a supported harness
     */
    public static List<String> reasons(GenerationBuildTool buildTool, Map<String, String> testsFiles) {
        Map<String, String> files = testsFiles == null ? Map.of() : testsFiles;
        boolean maven = buildTool == GenerationBuildTool.MAVEN;
        String buildFile = files.get(maven ? "pom.xml" : "build.gradle");
        if (buildFile == null) {
            return List.of(maven ? "Java tests must keep the seeded Maven harness file (pom.xml) containing the Ares 2 dependency and trusted-package protections."
                    : "Java tests must keep the seeded Gradle harness file containing the Ares 2 dependency and trusted-package protections.");
        }
        List<String> reasons = maven ? mavenReasons(buildFile) : gradleReasons(buildFile, files);
        String policy = files.get("SecurityPolicy.yaml");
        if (policy == null || policy.isBlank()) {
            reasons.add("Ares 2 tests must keep the explicit SecurityPolicy.yaml; the default policy must not derive exemptions from student code.");
        }
        return reasons;
    }

    private static List<String> gradleReasons(String gradle, Map<String, String> testsFiles) {
        String gradleWithoutComments = JavaSourceText.stripComments(gradle);
        List<String> reasons = new ArrayList<>();
        if (!ARES_2_DEPENDENCY.matcher(gradleWithoutComments).find() || gradleWithoutComments.contains("de.tum.in.ase:" + LEGACY_ARES_1 + ":")) {
            reasons.add("Java generation requires the current Ares 2 dependency (de.tum.cit.ase:ares) in tests/build.gradle; legacy Ares 1 harnesses are not supported.");
        }
        String reserved = testsFiles.getOrDefault("gradle/AresReservedPackages.gradle", "");
        if (!gradleWithoutComments.contains("apply from: 'gradle/AresReservedPackages.gradle'") || !reserved.contains("de/tum/cit/ase/ares/**")
                || !reserved.contains("org/junit/**")) {
            reasons.add("Java Ares 2 tests must keep the seeded AresReservedPackages.gradle guard and apply it from build.gradle.");
        }
        return reasons;
    }

    private static List<String> mavenReasons(String pom) {
        String pomWithoutComments = XML_COMMENT.matcher(pom).replaceAll("");
        List<String> reasons = new ArrayList<>();
        if (!ARES_2_MAVEN_DEPENDENCY.matcher(pomWithoutComments).find() || pomWithoutComments.contains(LEGACY_ARES_1)) {
            reasons.add("Java generation requires the current Ares 2 dependency (de.tum.cit.ase:ares) in tests/pom.xml; legacy Ares 1 harnesses are not supported.");
        }
        if (!pomWithoutComments.contains("<id>verify-ares-reserved-packages-v2</id>") || !pomWithoutComments.contains("<include name=\"de/tum/cit/ase/ares/**\"/>")
                || !pomWithoutComments.contains("<include name=\"org/junit/**\"/>")) {
            reasons.add("Java Ares 2 tests must keep the seeded reserved-package guard (verify-ares-reserved-packages-v2) in tests/pom.xml.");
        }
        return reasons;
    }
}
