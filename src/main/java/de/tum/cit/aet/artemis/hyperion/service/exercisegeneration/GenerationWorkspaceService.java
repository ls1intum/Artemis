package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Assembles the unified agent workspace (problem statement plus the template, solution, and test repositories, each in its own directory) into the sandbox and reads the produced
 * files back out, so the agent can make coherent cross-cutting changes across all components of an exercise.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class GenerationWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(GenerationWorkspaceService.class);

    static final String WORKSPACE = "/workspace";

    private static final String PROBLEM_STATEMENT_FILE = "problem-statement.md";

    private static final RepositoryType[] SEEDED_REPOSITORIES = { RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS };

    /** The repository directories the layout probe lists and scans for build manifests; matches {@link #directoryFor(RepositoryType)} for the seeded repositories. */
    private static final String[] REPOSITORY_DIRECTORIES = { "solution", "template", "tests" };

    /** How long the one-shot layout probe may run; it is only {@code ls} + {@code head} over the seeded tree, so this is generous. */
    private static final Duration LAYOUT_PROBE_TIMEOUT = Duration.ofSeconds(30);

    /** Upper bound on the size of the seeded-layout observation handed to the agent on turn 0, so a deeply nested tree cannot blow up the prompt. */
    private static final int LAYOUT_PROBE_MAX_CHARS = 6_000;

    /** The sandbox directory holding the read-only worked-sample reference (a complete example exercise for this language's test conventions); never extracted or persisted. */
    static final String REFERENCE_DIR = "reference";

    /** Per-file and total caps on the seeded reference payload, so a large template cannot bloat the workspace tar. */
    private static final int MAX_REFERENCE_FILE_BYTES = 64_000;

    private static final int MAX_REFERENCE_TOTAL_BYTES = 512_000;

    private final GitService gitService;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final SandboxBuildCommandService sandboxBuildCommandService;

    private final ResourceLoaderService resourceLoaderService;

    public GenerationWorkspaceService(GitService gitService, ProgrammingLanguageConfiguration programmingLanguageConfiguration,
            SandboxBuildCommandService sandboxBuildCommandService, ResourceLoaderService resourceLoaderService) {
        this.gitService = gitService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
        this.sandboxBuildCommandService = sandboxBuildCommandService;
        this.resourceLoaderService = resourceLoaderService;
    }

    /**
     * Builds the session spec from the exercise's LocalCI execution image. The container holds no secrets and uses the regular build network so dependencies resolve.
     *
     * @param exercise the exercise whose language/project type selects the image
     * @return the sandbox session specification
     */
    public SandboxSessionSpec sessionSpec(ProgrammingExercise exercise) {
        String image = programmingLanguageConfiguration.getImage(exercise.getProgrammingLanguage(), Optional.ofNullable(exercise.getProjectType()));
        return new SandboxSessionSpec(image, new DockerRunConfig(List.of(), null, 0, 0, 0));
    }

    /**
     * Checks out the repositories and seeds the problem statement, the {@code verify.sh} build helper, and all repository working trees into the sandbox as a single tar archive.
     * The repositories are packed from their checked-out working copies on disk so binary files and the executable bit (e.g. {@code gradlew}) survive into the container.
     *
     * @param sandbox   the sandbox session
     * @param sessionId the session handle
     * @param exercise  the exercise whose components are seeded
     * @return the seeded TESTS-repo text files keyed by repository-relative path (the harness snapshot used later by the immutability gate); empty if the tests repo was absent
     */
    public Map<String, String> seedWorkspace(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        String defaultBranch = exercise.getBuildConfig() != null ? exercise.getBuildConfig().getBranch() : null;
        Map<String, String> textFiles = new LinkedHashMap<>();
        textFiles.put(PROBLEM_STATEMENT_FILE, exercise.getProblemStatement() == null ? "" : exercise.getProblemStatement());
        textFiles.put(SandboxBuildCommandService.VERIFY_SCRIPT_NAME, sandboxBuildCommandService.verifyScriptContent(exercise));
        Map<String, Path> repositoryTrees = new LinkedHashMap<>();
        Map<String, String> testsSeedSnapshot = Map.of();
        for (RepositoryType repositoryType : SEEDED_REPOSITORIES) {
            Path workingTree = checkoutWorkingTree(exercise, repositoryType, defaultBranch);
            if (workingTree != null) {
                repositoryTrees.put(directoryFor(repositoryType), workingTree);
                // Snapshot the SEEDED tests-repo files so the verifier can later reject any harness tampering against this exact baseline (the harness is graded verbatim in
                // production). Read from the same on-disk working tree that is packed into the sandbox, so the snapshot is byte-identical to what the agent starts from.
                if (repositoryType == RepositoryType.TESTS) {
                    testsSeedSnapshot = readWorkingTreeTextFiles(workingTree);
                }
            }
        }
        // Reference lives under reference/ (not a repository directory) so the layout probe ignores it and it is never extracted or persisted (see readReferenceSample for WHY).
        Map<String, String> referenceSample = readReferenceSample(exercise);
        textFiles.putAll(referenceSample);
        sandbox.copyIn(sessionId, WORKSPACE, WorkspaceArchive.buildWorkspaceTarStream(textFiles, repositoryTrees));
        log.info("Seeded generation workspace for exercise {} ({} repositories, {} reference files)", exercise.getId(), repositoryTrees.size(), referenceSample.size());
        return testsSeedSnapshot;
    }

    /**
     * Reads the language's worked-sample TEXT files (its test harness + example tests, and the example solution) from the classpath templates, keyed {@code reference/<path>}, so
     * the
     * agent always has a complete working example of this language's test-framework conventions to study — even when the working repositories were stripped clean. Best-effort:
     * binary
     * files, oversized files, and any read error are skipped, and the total payload is bounded.
     *
     * @param exercise the exercise whose language (and, as a fallback, project type) selects the template tree
     * @return the reference files keyed by their archive-relative path under {@code reference/}, or empty if none could be read
     */
    Map<String, String> readReferenceSample(ProgrammingExercise exercise) {
        if (exercise.getProgrammingLanguage() == null) {
            return Map.of();
        }
        String languageDir = exercise.getProgrammingLanguage().name().toLowerCase(Locale.ROOT);
        Map<String, String> reference = new LinkedHashMap<>();
        int[] remainingBytes = { MAX_REFERENCE_TOTAL_BYTES };
        // The example tests (the test-framework wiring the agent must reproduce) and the example solution (so the tests read coherently). The build manifests within test/ are part
        // of
        // the harness reference too.
        for (String area : List.of("test", "solution")) {
            addReferenceArea(reference, languageDir, area, remainingBytes);
        }
        // Languages whose templates live only under a project-type subdirectory (e.g. C: templates/c/{gcc,fact}) have no language-level test/solution; fall back to the project
        // type.
        if (reference.isEmpty() && exercise.getProjectType() != null) {
            String projectTypeRelativeBase = languageDir + "/" + exercise.getProjectType().name().toLowerCase(Locale.ROOT);
            for (String area : List.of("test", "solution")) {
                addReferenceArea(reference, projectTypeRelativeBase, area, remainingBytes);
            }
        }
        return reference;
    }

    /**
     * Adds the readable text files under {@code templates/<languageRelativeBase>/<area>} to {@code reference}, keyed {@code reference/<area>/<rest>} (the path relative to the
     * language
     * template root), respecting the remaining byte budget. Robust across filesystem and jar resources via the {@code /templates/<languageRelativeBase>/} URI marker.
     */
    private void addReferenceArea(Map<String, String> reference, String languageRelativeBase, String area, int[] remainingBytes) {
        String marker = "/templates/" + languageRelativeBase + "/";
        Resource[] resources = resourceLoaderService.getFileResources(Path.of("templates").resolve(languageRelativeBase).resolve(area));
        for (Resource resource : resources) {
            if (remainingBytes[0] <= 0) {
                return;
            }
            try {
                String uri = resource.getURI().toString().replace('\\', '/');
                int markerIndex = uri.indexOf(marker);
                if (markerIndex < 0) {
                    continue;
                }
                String relativePath = uri.substring(markerIndex + marker.length());
                if (relativePath.isEmpty() || relativePath.endsWith("/")) {
                    continue;
                }
                byte[] content = resource.getInputStream().readAllBytes();
                if (content.length == 0 || content.length > MAX_REFERENCE_FILE_BYTES || BinaryContent.isBinary(content)) {
                    continue;
                }
                reference.put(REFERENCE_DIR + "/" + relativePath, new String(content, StandardCharsets.UTF_8));
                remainingBytes[0] -= content.length;
            }
            catch (IOException | RuntimeException e) {
                log.debug("Skipping reference sample resource {}: {}", resource, e.getMessage());
            }
        }
    }

    /**
     * Probes the freshly-seeded workspace ONCE and renders a compact, human-readable snapshot of its layout — a recursive listing of the {@code solution}, {@code template}, and
     * {@code tests} directories plus the first lines of whatever build manifests actually exist at their roots (pom.xml, build.gradle, Cargo.toml, package.json, go.mod, *.cabal,
     * dune-project, Makefile, …). This is handed to the agent on turn 0 as a free observation so it does not spend turns discovering the layout itself.
     * <p>
     * It is fully language- and project-type-agnostic: it never assumes a particular toolchain, it only lists the seeded tree and heads any manifest it finds. The whole thing is a
     * single {@code ls}/{@code find}/{@code head} shell invocation, bounded in size both in-shell and again in Java, and it degrades to an empty string on any error or timeout
     * (the
     * agent then simply falls back to listing the workspace itself, exactly as before). An empty repository contributes nothing, which is fine.
     *
     * @param sandbox   the sandbox session
     * @param sessionId the session handle
     * @return the rendered layout snapshot, or an empty string if it could not be produced
     */
    public String probeWorkspaceLayout(InteractiveSandbox sandbox, String sessionId) {
        // One shell pass: (1) `ls -R` the seeded repository dirs (silencing "No such file" for an absent repo), then (2) discover and `head` the build manifests that actually
        // exist
        // at or near each repo root. The manifest set is a broad, language-agnostic union; `find` only emits the ones present, so this never assumes a toolchain. Output is capped
        // in-shell with `head -c` as a first guard; Java truncates again defensively.
        String script = "cd " + WORKSPACE + " 2>/dev/null || exit 0\n" + "echo '--- ls -R " + String.join(" ", REPOSITORY_DIRECTORIES) + " ---'\n" + "ls -R "
                + String.join(" ", REPOSITORY_DIRECTORIES) + " 2>/dev/null\n" + "for f in $(find " + String.join(" ", REPOSITORY_DIRECTORIES)
                + " -maxdepth 2 -type f \\( -name pom.xml -o -name 'build.gradle' -o -name 'build.gradle.kts' "
                + "-o -name 'settings.gradle' -o -name 'settings.gradle.kts' -o -name Cargo.toml -o -name package.json -o -name go.mod -o -name Makefile -o -name CMakeLists.txt "
                + "-o -name dune-project -o -name dune -o -name '*.cabal' -o -name stack.yaml -o -name pyproject.toml -o -name setup.py -o -name requirements.txt -o -name Gemfile "
                + "-o -name '*.csproj' -o -name build.sbt -o -name Package.swift -o -name pubspec.yaml -o -name DESCRIPTION -o -name composer.json -o -name '*.bats' \\) "
                + "2>/dev/null | sort); do\n" + "  echo; echo \"--- head -40 $f ---\"; head -40 \"$f\" 2>/dev/null\n" + "done\n"
                // Surface the read-only worked-sample reference so the agent discovers it (it is NOT a repository dir, so it is otherwise invisible to this listing). Bounded.
                + "if [ -d " + REFERENCE_DIR + " ]; then echo; echo '--- ls -R " + REFERENCE_DIR
                + " (read-only worked example: study it for this language test-framework conventions; do not edit or copy it) ---'; ls -R " + REFERENCE_DIR
                + " 2>/dev/null | head -c 1500; fi\n";
        try {
            SandboxExecResult result = sandbox.exec(sessionId, LAYOUT_PROBE_TIMEOUT, "sh", "-c", script);
            if (result.timedOut()) {
                return "";
            }
            String layout = result.combinedOutput();
            return layout == null ? "" : truncateLayout(layout.strip());
        }
        catch (RuntimeException e) {
            // Best-effort only: if the probe fails the agent still has its own tools to list the workspace, so swallow and return nothing.
            log.warn("Could not probe the seeded workspace layout: {}", e.getMessage());
            return "";
        }
    }

    /** Caps the layout snapshot at {@link #LAYOUT_PROBE_MAX_CHARS}, appending a short notice when it was truncated so the agent knows to list deeper itself if needed. */
    private static String truncateLayout(String layout) {
        if (layout.length() <= LAYOUT_PROBE_MAX_CHARS) {
            return layout;
        }
        return layout.substring(0, LAYOUT_PROBE_MAX_CHARS) + "\n… [workspace layout truncated; list deeper directories yourself with `ls -R` if you need more]";
    }

    /**
     * Reads the text files of a checked-out working tree into a repository-relative map (UTF-8), skipping {@code .git} metadata, so the seeded tests harness can be snapshotted for
     * the later immutability gate. Binary files and unreadable files are skipped silently; this is best-effort and a partial snapshot only weakens the gate, never breaks the run.
     *
     * @param workingTree the checked-out repository working tree
     * @return the text files keyed by repository-relative path
     */
    private static Map<String, String> readWorkingTreeTextFiles(Path workingTree) {
        Map<String, String> files = new LinkedHashMap<>();
        try (var paths = Files.walk(workingTree)) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                String relative = workingTree.relativize(path).toString().replace('\\', '/');
                if (relative.isEmpty() || relative.equals(".git") || relative.startsWith(".git/") || relative.contains("/.git/")) {
                    continue;
                }
                try {
                    files.put(relative, Files.readString(path));
                }
                catch (IOException | RuntimeException e) {
                    // A binary or unreadable file is not part of the text harness; skip it.
                }
            }
        }
        catch (IOException | RuntimeException e) {
            log.warn("Could not snapshot the seeded tests harness: {}", e.getMessage());
        }
        return files;
    }

    /**
     * The produced files of a repository read back out of the sandbox, plus whether the read-back FAILED. A failed read-back ({@code extractionFailed=true}) is distinct from a
     * genuinely empty repository (extraction succeeded, {@code files} empty): the verifier fails CLOSED on the former (it cannot run the integrity gates on missing files) but
     * stays
     * fail-open on the latter.
     *
     * @param files            the produced files keyed by repository-relative path (empty if the repo is genuinely empty OR extraction failed)
     * @param extractionFailed {@code true} if reading the repository out of the sandbox threw — an error, not a genuinely empty repo
     */
    public record RepositoryExtraction(Map<String, String> files, boolean extractionFailed) {
    }

    /**
     * Convenience: the {@link RepositoryExtraction#files()} of {@link #extractRepository}, dropping the extraction-failed flag.
     *
     * @param sandbox        the sandbox to read from
     * @param sessionId      the sandbox session
     * @param repositoryType the repository to extract
     * @return the produced files keyed by repository-relative path
     */
    public Map<String, String> extractRepositoryFiles(InteractiveSandbox sandbox, String sessionId, RepositoryType repositoryType) {
        return extractRepository(sandbox, sessionId, repositoryType).files();
    }

    /**
     * Reads the produced files of a repository back out of the sandbox, reporting whether the read-back FAILED (so the verifier can fail closed on a genuine extraction error while
     * staying fail-open on a genuinely empty repo). Uses the tar API rather than per-file reads so large files are never truncated.
     *
     * @param sandbox        the sandbox session
     * @param sessionId      the session handle
     * @param repositoryType the repository whose files to read back
     * @return the produced files and an extraction-failed flag
     */
    public RepositoryExtraction extractRepository(InteractiveSandbox sandbox, String sessionId, RepositoryType repositoryType) {
        String dir = directoryFor(repositoryType);
        try (TarArchiveInputStream tar = sandbox.copyOut(sessionId, WORKSPACE + "/" + dir)) {
            // Docker prefixes copied-out entries with the source directory's own name.
            Map<String, String> files = stripRedundantGitkeeps(WorkspaceArchive.readTar(tar, dir));
            // For the student-facing TEMPLATE and the reference SOLUTION, additionally drop orphan residue: source files left behind OUTSIDE the canonical roots (a nested
            // assignment/ solution/ template/ tree, e.g. a scaffold's reference copy under template/assignment/solution/src or an orphan solution/solution/src). Shipping these
            // would leak the solution to students or inflate the solution-vs-template diff. The tests repo keeps all its files (its harness genuinely lives at the root).
            if (repositoryType == RepositoryType.TEMPLATE || repositoryType == RepositoryType.SOLUTION) {
                files = ExerciseIntegrityGate.stripResidueOutsideCanonicalRoots(files);
            }
            return new RepositoryExtraction(files, false);
        }
        catch (RuntimeException | IOException e) {
            log.warn("Could not extract {} files for exercise generation: {}", repositoryType, e.getMessage());
            return new RepositoryExtraction(Map.of(), true);
        }
    }

    /**
     * Drops every {@code .gitkeep} that sits in a directory which now also contains a real produced file. The scaffold seeds a {@code .gitkeep} into each emptied source directory
     * (e.g. {@code src/.gitkeep}) only so the empty directory survives version control; once the agent writes a real source into that directory the marker is vestigial clutter
     * that
     * would otherwise ship in the student-facing repository (and inflate the solution-vs-template diff). A {@code .gitkeep} whose directory is still otherwise empty is kept, so an
     * intentionally-empty directory still survives. Deterministic post-processing is more reliable than asking the LLM to clean these up.
     *
     * @param files the produced files keyed by repository-relative path
     * @return the same map without redundant {@code .gitkeep} markers
     */
    static Map<String, String> stripRedundantGitkeeps(Map<String, String> files) {
        Set<String> directoriesWithRealFiles = new HashSet<>();
        for (String path : files.keySet()) {
            if (!isGitkeep(path)) {
                directoriesWithRealFiles.add(parentDirectory(path));
            }
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            if (isGitkeep(entry.getKey()) && directoriesWithRealFiles.contains(parentDirectory(entry.getKey()))) {
                continue;
            }
            cleaned.put(entry.getKey(), entry.getValue());
        }
        return cleaned;
    }

    private static boolean isGitkeep(String path) {
        return path.equals(".gitkeep") || path.endsWith("/.gitkeep");
    }

    private static String parentDirectory(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash < 0 ? "" : path.substring(0, lastSlash);
    }

    /**
     * Reads the produced problem statement back out of the sandbox.
     *
     * @param sandbox   the sandbox session
     * @param sessionId the session handle
     * @return the produced problem statement, or an empty string if it could not be read
     */
    public String extractProblemStatement(InteractiveSandbox sandbox, String sessionId) {
        try (TarArchiveInputStream tar = sandbox.copyOut(sessionId, WORKSPACE + "/" + PROBLEM_STATEMENT_FILE)) {
            return WorkspaceArchive.readTar(tar, "").getOrDefault(PROBLEM_STATEMENT_FILE, "");
        }
        catch (RuntimeException | IOException e) {
            log.warn("Could not extract the problem statement for exercise generation: {}", e.getMessage());
            return "";
        }
    }

    private Path checkoutWorkingTree(ProgrammingExercise exercise, RepositoryType repositoryType, String defaultBranch) {
        LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
        if (uri == null) {
            return null;
        }
        try {
            Repository repository = gitService.getOrCheckoutRepository(uri, true, defaultBranch, false);
            return repository == null ? null : repository.getLocalPath();
        }
        catch (Exception e) {
            log.warn("Could not check out {} repository for exercise {}: {}", repositoryType, exercise.getId(), e.getMessage());
            return null;
        }
    }

    static String directoryFor(RepositoryType repositoryType) {
        return switch (repositoryType) {
            case TEMPLATE -> "template";
            case SOLUTION -> "solution";
            case TESTS -> "tests";
            default -> repositoryType.name().toLowerCase(Locale.ROOT);
        };
    }
}
