package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.util.List;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ExerciseIntegrityGate;

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
        String lower = command.toLowerCase();
        if (!lower.matches(
                "(?s).*(?:tests|solution|template)/(buildsrc/.*|gradle/.*|pom\\.xml|build\\.gradle|build\\.gradle\\.kts|settings\\.gradle|settings\\.gradle\\.kts|gradle\\.properties|package\\.json|"
                        + "package-lock\\.json|pnpm-lock\\.yaml|yarn\\.lock|tsconfig\\.json|cargo\\.toml|cargo\\.lock|.*\\.cabal).*")) {
            return false;
        }
        return lower.contains(">") || lower.contains("sed -i") || lower.contains("perl -pi") || lower.contains(" tee ") || lower.startsWith("tee ") || lower.contains(" rm ")
                || lower.startsWith("rm ") || lower.contains(" mv ") || lower.startsWith("mv ") || lower.contains(" cp ") || lower.startsWith("cp ");
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
