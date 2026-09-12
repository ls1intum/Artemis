package de.tum.cit.aet.artemis.hyperionworker.generation.agent;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.runtime.security.HyperionSecretMaterialPolicy;
import de.tum.cit.aet.artemis.hyperionworker.generation.verification.ExerciseIntegrityGate;

/**
 * Model steering for the sandbox tools' path arguments. <strong>Not a security boundary.</strong>
 * <p>
 * Every check here operates on the string the model passed to a structured tool, so it is trivially bypassable: the same session exposes an unrestricted {@code bash} tool, and
 * {@link #workspaceRelativePath} normalises text rather than resolving the filesystem, so a symlink created inside the workspace reaches outside it. Its purpose is to turn the
 * common accident — the model deciding to "fix" the build by editing the seeded harness — into an immediate, explanatory tool observation instead of a verification failure ten
 * turns later. The actual containment lives in the sandbox container, in the link-rejecting {@code copyOut} read-back ({@code WorkspaceArchive}, {@code CollectedReports}) that
 * decides which bytes may reach a Git commit, and in {@code ExerciseIntegrityGate} plus the differential verifier, which re-derive harness immutability from the produced
 * artefacts.
 */
final class SandboxPathPolicy {

    private static final String WORKSPACE = "/workspace";

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    /**
     * The {@code .gradle} and {@code .m2} cache directories (as a path segment, so {@code settings.gradle} is not one), the {@code javap} disassembler, and any JAR file, on a
     * lowercased command line.
     */
    private static final Pattern INSPECTS_DEPENDENCY_ARTIFACTS = Pattern.compile("(?:^|[\\s/~'\"=])\\.(?:gradle|m2)(?=[/\\s'\"]|$)|\\bjavap\\b|\\.jar\\b");

    private SandboxPathPolicy() {
    }

    static boolean isManagedBuildInfrastructurePath(String path) {
        for (String repository : List.of("solution/", "template/", "tests/")) {
            if (path.startsWith(repository)) {
                String repositoryPath = path.substring(repository.length());
                return repositoryPath.startsWith("buildSrc/") || repositoryPath.startsWith("gradle/") || repositoryPath.startsWith(".mvn/") || repositoryPath.startsWith(".m2/")
                        || repositoryPath.startsWith("target/") || repositoryPath.startsWith("build/") || ExerciseIntegrityGate.isHarnessFile(repositoryPath);
            }
        }
        return false;
    }

    static boolean isWritableGenerationPath(String path) {
        return path.equals("SPEC.md") || path.equals("test-plan.json") || path.equals("problem-statement.md") || path.startsWith("solution/") || path.startsWith("template/")
                || path.startsWith("tests/");
    }

    static String immutableHarnessError(String path) {
        return "ERROR: do not modify " + path + ". Repository build infrastructure is seeded and managed by Artemis; edit only the problem statement and exercise source files.";
    }

    /**
     * A best-effort textual guess at whether a {@code bash} command line would rewrite seeded build infrastructure, used to answer with the same explanatory observation the
     * structured edit tools give. It pattern-matches a lowercased command string, so quoting, variables, {@code $(...)}, an editor, or a script file all evade it.
     *
     * @param command the command line the model asked to run
     * @return whether it looks like a rewrite of managed build infrastructure
     */
    static boolean mutatesManagedBuildInfrastructure(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        if (!lower.matches(
                "(?s).*(?:tests|solution|template)/(buildsrc/.*|gradle/.*|pom\\.xml|build\\.gradle|build\\.gradle\\.kts|settings\\.gradle|settings\\.gradle\\.kts|gradle\\.properties|package\\.json|"
                        + "package-lock\\.json|pnpm-lock\\.yaml|yarn\\.lock|tsconfig\\.json|cargo\\.toml|cargo\\.lock|.*\\.cabal).*")) {
            return false;
        }
        return lower.contains(">") || lower.contains("sed -i") || lower.contains("perl -pi") || lower.contains(" tee ") || lower.startsWith("tee ") || lower.contains(" rm ")
                || lower.startsWith("rm ") || lower.contains(" mv ") || lower.startsWith("mv ") || lower.contains(" cp ") || lower.startsWith("cp ");
    }

    /**
     * A best-effort textual guess at whether a {@code bash} command line reads the dependency cache or disassembles a dependency JAR, so that the observation can name the
     * contract instead. The build is offline and the harness immutable, so nothing in the cache is actionable; a model that starts reading Ares bytecode there spends every
     * remaining step on it and never writes a test. Same textual best effort as {@link #mutatesManagedBuildInfrastructure}.
     *
     * @param command the command line the model asked to run
     * @return whether it looks like an inspection of dependency artifacts
     */
    static boolean inspectsDependencyArtifacts(String command) {
        return INSPECTS_DEPENDENCY_ARTIFACTS.matcher(command.toLowerCase(Locale.ROOT)).find();
    }

    static String dependencyArtifactsError() {
        return "exit=2\nThe dependency cache and dependency JARs are not inspectable: the build is offline and the test harness is immutable, so nothing in there is actionable. "
                + "The Ares and JUnit API to use (@Public, @WhitelistPath, @BlacklistPath, @StrictTimeout, ReflectionTestUtils) is stated in your instructions and shown in "
                + "reference/style/tests.md. Write the tests against that contract instead.";
    }

    static String invalidPathError(String path) {
        String safePath = SECRET_MATERIAL_POLICY.assess(path, new byte[0], HyperionSecretMaterialPolicy.Origin.TOOL_OBSERVATION).safePath();
        return "ERROR: invalid path '" + safePath + "'. Use a workspace-relative path containing only letters, digits, '_', '.', '/', '-' and no '..'.";
    }

    static @Nullable String workspaceRelativePath(@Nullable String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String trimmed = path.trim();
        if (trimmed.startsWith(WORKSPACE + "/")) {
            trimmed = trimmed.substring((WORKSPACE + "/").length());
        }
        return trimmed.startsWith("/") || trimmed.contains("..") || !trimmed.matches("[a-zA-Z0-9_./-]+") ? null : trimmed;
    }
}
