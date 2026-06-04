package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
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

    private final GitService gitService;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final SandboxBuildCommandService buildCommandFactory;

    public GenerationWorkspaceService(GitService gitService, ProgrammingLanguageConfiguration programmingLanguageConfiguration, SandboxBuildCommandService buildCommandFactory) {
        this.gitService = gitService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
        this.buildCommandFactory = buildCommandFactory;
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
        textFiles.put(SandboxBuildCommandService.VERIFY_SCRIPT_NAME, buildCommandFactory.verifyScriptContent(exercise));
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
        sandbox.copyIn(sessionId, WORKSPACE, WorkspaceArchive.buildWorkspaceTarStream(textFiles, repositoryTrees));
        log.info("Seeded generation workspace for exercise {} ({} repositories)", exercise.getId(), repositoryTrees.size());
        return testsSeedSnapshot;
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
     * Reads the produced files of a repository back out of the sandbox. Uses the tar API rather than per-file reads so large files are never truncated.
     *
     * @param sandbox        the sandbox session
     * @param sessionId      the session handle
     * @param repositoryType the repository whose files to read back
     * @return the produced files keyed by repository-relative path, or empty if extraction failed
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
