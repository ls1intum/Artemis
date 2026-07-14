package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionContext;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.ExerciseIntegrityGate;
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
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(GenerationWorkspaceService.class);

    public static final String WORKSPACE = "/workspace";

    private static final String PROBLEM_STATEMENT_FILE = "problem-statement.md";

    private static final RepositoryType[] SEEDED_REPOSITORIES = { RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS };

    /** Repository directories the layout probe lists and scans for build manifests; matches {@link #directoryFor(RepositoryType)}. */
    private static final String[] REPOSITORY_DIRECTORIES = { "solution", "template", "tests" };

    private static final Duration LAYOUT_PROBE_TIMEOUT = Duration.ofSeconds(30);

    /** Upper bound on the turn-0 layout observation so a deeply nested tree cannot blow up the prompt. */
    private static final int LAYOUT_PROBE_MAX_CHARS = 6_000;

    /** Sandbox directory holding the read-only worked-sample reference; never extracted or persisted. */
    static final String REFERENCE_DIR = "reference";

    /** Per-file and total caps on the seeded reference payload, so a large template cannot bloat the workspace tar. */
    private static final int MAX_REFERENCE_FILE_BYTES = 64_000;

    private static final int MAX_REFERENCE_TOTAL_BYTES = 512_000;

    private final GitService gitService;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final SandboxBuildCommandService sandboxBuildCommandService;

    private final ResourceLoaderService resourceLoaderService;

    private final TempFileUtilService tempFileUtilService;

    public GenerationWorkspaceService(GitService gitService, ProgrammingLanguageConfiguration programmingLanguageConfiguration,
            SandboxBuildCommandService sandboxBuildCommandService, ResourceLoaderService resourceLoaderService, TempFileUtilService tempFileUtilService) {
        this.gitService = gitService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
        this.sandboxBuildCommandService = sandboxBuildCommandService;
        this.resourceLoaderService = resourceLoaderService;
        this.tempFileUtilService = tempFileUtilService;
    }

    /**
     * Builds the session spec from the exercise's LocalCI execution image. The container holds no secrets and disables Docker networking; generated code must not have egress.
     *
     * @param exercise the exercise whose language/project type selects the image
     * @return the sandbox session spec
     */
    public SandboxSessionSpec sessionSpec(ProgrammingExercise exercise) {
        return sessionSpec(exercise, null);
    }

    public SandboxSessionSpec sessionSpec(ProgrammingExercise exercise, @Nullable SandboxSessionContext context) {
        String image = programmingLanguageConfiguration.getImage(exercise.getProgrammingLanguage(), Optional.ofNullable(exercise.getProjectType()));
        return new SandboxSessionSpec(image, new DockerRunConfig(List.of(), "none", 0, 0, 0), context);
    }

    /**
     * Checks out the repositories and seeds the problem statement, the {@code verify.sh} build helper, and all repository working trees into the sandbox as a single tar archive.
     * The repositories are packed from their checked-out working copies on disk so binary files and the executable bit (e.g. {@code gradlew}) survive into the container.
     *
     * @param sandbox   the sandbox session
     * @param sessionId the session handle
     * @param exercise  the exercise whose components are seeded
     * @param mode      whether to start from clean exercise artifacts or preserve the existing tree
     * @return the seeded repository heads plus TESTS-repo text files used later by the immutability and stale-head gates
     */
    public WorkspaceSeed seedWorkspace(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, GenerationMode mode) {
        String defaultBranch = exercise.getBuildConfig() != null ? exercise.getBuildConfig().getBranch() : null;
        Map<String, String> textFiles = new LinkedHashMap<>();
        textFiles.put(PROBLEM_STATEMENT_FILE, exercise.getProblemStatement() == null ? "" : exercise.getProblemStatement());
        textFiles.put(SandboxBuildCommandService.VERIFY_SCRIPT_NAME, sandboxBuildCommandService.verifyScriptContent(exercise));
        Map<String, Path> repositoryTrees = new LinkedHashMap<>();
        Map<RepositoryType, String> repositoryHeads = new LinkedHashMap<>();
        Map<RepositoryType, RepositorySeedMetadata> repositoryMetadata = new LinkedHashMap<>();
        Map<RepositoryType, Map<String, String>> repositoryTextFiles = new LinkedHashMap<>();
        Map<String, String> testsSeedSnapshot = Map.of();
        List<SeededRepository> temporaryCheckouts = new ArrayList<>();
        try {
            for (RepositoryType repositoryType : SEEDED_REPOSITORIES) {
                SeededRepository seededRepository = checkoutWorkingTree(exercise, repositoryType, defaultBranch);
                temporaryCheckouts.add(seededRepository);
                prepareRepositoryForMode(seededRepository.workingTree(), repositoryType, mode);
                repositoryTrees.put(directoryFor(repositoryType), seededRepository.workingTree());
                repositoryHeads.put(repositoryType, seededRepository.headHash());
                repositoryMetadata.put(repositoryType, readWorkingTreeMetadata(seededRepository.workingTree()));
                Map<String, String> textSnapshot = Map.copyOf(readWorkingTreeTextFiles(seededRepository.workingTree()));
                repositoryTextFiles.put(repositoryType, textSnapshot);
                if (repositoryType == RepositoryType.TESTS) {
                    testsSeedSnapshot = textSnapshot;
                }
            }
            Map<String, String> referenceSample = readReferenceSample(exercise);
            textFiles.putAll(referenceSample);
            sandbox.copyIn(sessionId, WORKSPACE, WorkspaceArchive.buildWorkspaceTarStream(textFiles, repositoryTrees));
            log.info("Seeded generation workspace for exercise {} ({} repositories, {} reference files)", exercise.getId(), repositoryTrees.size(), referenceSample.size());
            return new WorkspaceSeed(testsSeedSnapshot, Map.copyOf(repositoryHeads), Map.copyOf(repositoryMetadata), Map.copyOf(repositoryTextFiles));
        }
        finally {
            temporaryCheckouts.forEach(GenerationWorkspaceService::closeAndDeleteTemporaryCheckout);
        }
    }

    static void prepareRepositoryForMode(Path repositoryRoot, RepositoryType repositoryType, GenerationMode mode) {
        if (mode != GenerationMode.GENERATE) {
            return;
        }
        List<String> artifactRoots = repositoryType == RepositoryType.TESTS ? List.of("test", "behavior/test", "structural/test") : List.of("src");
        try {
            for (String artifactRoot : artifactRoots) {
                FileUtils.deleteDirectory(repositoryRoot.resolve(artifactRoot).toFile());
            }
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not prepare a clean " + repositoryType + " repository for generation", e);
        }
    }

    public record WorkspaceSeed(Map<String, String> testsSeedSnapshot, Map<RepositoryType, String> repositoryHeads, Map<RepositoryType, RepositorySeedMetadata> repositoryMetadata,
            Map<RepositoryType, Map<String, String>> repositoryTextFiles) {

        public WorkspaceSeed(Map<String, String> testsSeedSnapshot, Map<RepositoryType, String> repositoryHeads) {
            this(testsSeedSnapshot, repositoryHeads, Map.of(), Map.of());
        }

        public WorkspaceSeed(Map<String, String> testsSeedSnapshot, Map<RepositoryType, String> repositoryHeads, Map<RepositoryType, RepositorySeedMetadata> repositoryMetadata) {
            this(testsSeedSnapshot, repositoryHeads, repositoryMetadata, Map.of());
        }
    }

    public record RepositorySeedMetadata(Map<String, String> binaryDigests, Set<String> executableFiles) {

        public static final RepositorySeedMetadata EMPTY = new RepositorySeedMetadata(Map.of(), Set.of());
    }

    /**
     * Reads the language's worked-sample TEXT files (example tests + example solution) from the classpath templates, keyed {@code reference/<path>}, so the agent always has a
     * complete working example of this language's test-framework conventions even when the working repositories were stripped clean. Best-effort: binary/oversized/unreadable files
     * are skipped and the total payload is bounded.
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
                byte[] content;
                try (var input = resource.getInputStream()) {
                    content = input.readAllBytes();
                }
                if (content.length == 0 || content.length > MAX_REFERENCE_FILE_BYTES || content.length > remainingBytes[0] || BinaryContent.isBinary(content)) {
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
     * Renders a bounded turn-zero snapshot of repository paths and build manifests so the agent need not spend turns discovering the seeded layout. Returns an empty string on
     * failure so the agent can inspect the workspace itself.
     *
     * @param sandbox   the sandbox session
     * @param sessionId the session handle
     * @return the rendered layout snapshot, or an empty string if it could not be produced
     */
    public String probeWorkspaceLayout(InteractiveSandbox sandbox, String sessionId) {
        // One shell pass: ls -R the repo dirs, then find+head the build manifests present at their roots (broad language-agnostic union; find emits only the ones that exist).
        String script = "cd " + WORKSPACE + " 2>/dev/null || exit 0\n" + "echo '--- ls -R " + String.join(" ", REPOSITORY_DIRECTORIES) + " ---'\n" + "ls -R "
                + String.join(" ", REPOSITORY_DIRECTORIES) + " 2>/dev/null\n" + "for f in $(find " + String.join(" ", REPOSITORY_DIRECTORIES)
                + " -maxdepth 2 -type f \\( -name pom.xml -o -name 'build.gradle' -o -name 'build.gradle.kts' "
                + "-o -name 'settings.gradle' -o -name 'settings.gradle.kts' -o -name Cargo.toml -o -name package.json -o -name go.mod -o -name Makefile -o -name CMakeLists.txt "
                + "-o -name dune-project -o -name dune -o -name '*.cabal' -o -name stack.yaml -o -name pyproject.toml -o -name setup.py -o -name requirements.txt -o -name Gemfile "
                + "-o -name '*.csproj' -o -name build.sbt -o -name Package.swift -o -name pubspec.yaml -o -name DESCRIPTION -o -name composer.json -o -name '*.bats' \\) "
                + "2>/dev/null | sort); do\n" + "  echo; echo \"--- head -40 $f ---\"; head -40 \"$f\" 2>/dev/null\n" + "done\n"
                // Surface the reference dir so the agent discovers it (it is not a repository dir, so the listing above misses it).
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
            log.warn("Could not probe the seeded workspace layout: {}", e.getMessage());
            return "";
        }
    }

    /** Caps the layout snapshot at {@link #LAYOUT_PROBE_MAX_CHARS}, appending a truncation notice so the agent knows to list deeper itself. */
    private static String truncateLayout(String layout) {
        if (layout.length() <= LAYOUT_PROBE_MAX_CHARS) {
            return layout;
        }
        return layout.substring(0, LAYOUT_PROBE_MAX_CHARS) + "\n… [workspace layout truncated; list deeper directories yourself with `ls -R` if you need more]";
    }

    /**
     * Reads a working tree's text files into a repository-relative UTF-8 map, skipping {@code .git} metadata, to snapshot the seeded tests harness for the immutability gate.
     * Best-effort: binary/unreadable files are skipped; a partial snapshot only weakens the gate, never breaks the run.
     *
     * @param workingTree the checked-out repository working tree
     * @return the text files keyed by repository-relative path
     */
    private static Map<String, String> readWorkingTreeTextFiles(Path workingTree) {
        Map<String, String> files = new LinkedHashMap<>();
        long total = 0;
        try (var paths = Files.walk(workingTree)) {
            for (Path path : (Iterable<Path>) paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))::iterator) {
                String relative = workingTree.relativize(path).toString().replace('\\', '/');
                if (relative.isEmpty() || relative.equals(".git") || relative.startsWith(".git/") || relative.contains("/.git/")) {
                    continue;
                }
                try {
                    long size = Files.size(path);
                    if (size > WorkspaceArchive.MAX_FILE_BYTES || total + size > WorkspaceArchive.MAX_TOTAL_BYTES) {
                        continue;
                    }
                    byte[] content = Files.readAllBytes(path);
                    total += content.length;
                    if (!BinaryContent.isBinary(content)) {
                        files.put(relative, new String(content, StandardCharsets.UTF_8));
                    }
                }
                catch (IOException | RuntimeException e) {
                    // Binary or unreadable file: not part of the text harness, skip.
                }
            }
        }
        catch (IOException | RuntimeException e) {
            log.warn("Could not snapshot the seeded tests harness: {}", e.getMessage());
        }
        return files;
    }

    private static RepositorySeedMetadata readWorkingTreeMetadata(Path workingTree) {
        Map<String, String> digests = new LinkedHashMap<>();
        Set<String> executableFiles = new LinkedHashSet<>();
        try (var paths = Files.walk(workingTree)) {
            for (Path path : (Iterable<Path>) paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))::iterator) {
                String relative = workingTree.relativize(path).toString().replace('\\', '/');
                if (relative.equals(".git") || relative.startsWith(".git/") || relative.contains("/.git/")) {
                    continue;
                }
                if (Files.size(path) > WorkspaceArchive.MAX_FILE_BYTES) {
                    throw new IllegalStateException("The seeded repository contains an oversized file: " + relative);
                }
                if (Files.isExecutable(path)) {
                    executableFiles.add(relative);
                }
                byte[] content = Files.readAllBytes(path);
                if (BinaryContent.isBinary(content)) {
                    digests.put(relative, WorkspaceArchive.sha256(content));
                }
            }
        }
        catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not fingerprint the seeded repository binaries", e);
        }
        return new RepositorySeedMetadata(Map.copyOf(digests), Set.copyOf(executableFiles));
    }

    /**
     * The produced files of a repository read back out of the sandbox, plus whether the result could not be represented safely for persistence. The verifier fails closed when
     * {@code extractionFailed} is true.
     *
     * @param files            the produced files keyed by repository-relative path (empty if the repo is genuinely empty OR extraction failed)
     * @param extractionFailed {@code true} when extraction failed or the produced tree contains unsupported residue or binary changes
     */
    public record RepositoryExtraction(Map<String, String> files, boolean extractionFailed) {
    }

    /**
     * Overwrites repository text files with the canonical bytes that verification and persistence must share.
     *
     * @param sandbox            the sandbox session
     * @param sessionId          the session handle
     * @param filesByRepository  the canonical repository text files
     * @param repositoryMetadata seeded file metadata used to preserve executable modes
     */
    public void materializeRepositoryFiles(InteractiveSandbox sandbox, String sessionId, Map<RepositoryType, Map<String, String>> filesByRepository,
            Map<RepositoryType, RepositorySeedMetadata> repositoryMetadata) {
        Map<String, String> workspaceFiles = new LinkedHashMap<>();
        Set<String> executableFiles = new LinkedHashSet<>();
        filesByRepository.forEach((repositoryType, files) -> files.forEach((path, content) -> workspaceFiles.put(directoryFor(repositoryType) + "/" + path, content)));
        repositoryMetadata.forEach((repositoryType, metadata) -> metadata.executableFiles().forEach(path -> executableFiles.add(directoryFor(repositoryType) + "/" + path)));
        sandbox.copyIn(sessionId, WORKSPACE, WorkspaceArchive.buildWorkspaceTarStream(workspaceFiles, Map.of(), executableFiles));
    }

    /**
     * The {@link RepositoryExtraction#files()} of {@link #extractRepository}, dropping the extraction-failed flag.
     *
     * @param sandbox        the sandbox to read from
     * @param sessionId      the sandbox session
     * @param repositoryType the repository to extract
     * @return the produced files keyed by repository-relative path
     */
    public Map<String, String> extractRepositoryFiles(InteractiveSandbox sandbox, String sessionId, RepositoryType repositoryType) {
        return extractRepository(sandbox, sessionId, repositoryType, null).files();
    }

    /**
     * Reads the produced files of a repository back out of the sandbox. Uses the tar API rather than per-file reads so large files are never truncated.
     *
     * @param sandbox          the sandbox session
     * @param sessionId        the session handle
     * @param repositoryType   the repository whose files to read back
     * @param expectedMetadata the seeded binary digests and executable paths, or {@code null} for an advisory text-only read
     * @return the produced files and an extraction-failed flag
     */
    public RepositoryExtraction extractRepository(InteractiveSandbox sandbox, String sessionId, RepositoryType repositoryType, RepositorySeedMetadata expectedMetadata) {
        String dir = directoryFor(repositoryType);
        try (TarArchiveInputStream tar = sandbox.copyOut(sessionId, WORKSPACE + "/" + dir)) {
            // Docker prefixes copied-out entries with the source directory's own name.
            WorkspaceArchive.ArchiveContents contents = WorkspaceArchive.readTarContents(tar, dir);
            Map<String, String> files = contents.textFiles();
            if (expectedMetadata != null
                    && (!expectedMetadata.binaryDigests().equals(contents.binaryDigests()) || !expectedMetadata.executableFiles().equals(contents.executableFiles()))) {
                return new RepositoryExtraction(files, true);
            }
            if (repositoryType == RepositoryType.TEMPLATE || repositoryType == RepositoryType.SOLUTION) {
                Map<String, String> cleanedFiles = ExerciseIntegrityGate.stripResidueOutsideCanonicalRoots(files);
                if (!cleanedFiles.equals(files)) {
                    return new RepositoryExtraction(cleanedFiles, true);
                }
            }
            return new RepositoryExtraction(files, false);
        }
        catch (RuntimeException | IOException e) {
            log.warn("Could not extract {} files for exercise generation: {}", repositoryType, e.getMessage());
            return new RepositoryExtraction(Map.of(), true);
        }
    }

    /**
     * Reads the produced problem statement back out of the sandbox.
     *
     * @param sandbox   the sandbox session
     * @param sessionId the session handle
     * @return the produced problem statement
     */
    public String extractProblemStatement(InteractiveSandbox sandbox, String sessionId) {
        try (TarArchiveInputStream tar = sandbox.copyOut(sessionId, WORKSPACE + "/" + PROBLEM_STATEMENT_FILE)) {
            String statement = WorkspaceArchive.readTar(tar, "").get(PROBLEM_STATEMENT_FILE);
            if (statement == null) {
                throw new IllegalStateException("The generated problem statement is missing");
            }
            return statement;
        }
        catch (RuntimeException | IOException e) {
            throw new IllegalStateException("Could not extract the generated problem statement", e);
        }
    }

    private SeededRepository checkoutWorkingTree(ProgrammingExercise exercise, RepositoryType repositoryType, String defaultBranch) {
        LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
        if (uri == null) {
            throw new IllegalStateException("The " + repositoryType.name() + " repository is missing");
        }
        Path temporaryRoot = null;
        Repository repository = null;
        try {
            temporaryRoot = tempFileUtilService.createTempDirectory("hyperion-seed-");
            repository = gitService.getOrCheckoutRepository(uri, uri, temporaryRoot.resolve("repository"), true, defaultBranch, false);
            if (repository == null) {
                throw new IllegalStateException("checkout returned no repository");
            }
            String headHash = gitService.getLocalHeadHash(repository);
            if (headHash == null) {
                throw new IllegalStateException("repository has no HEAD");
            }
            return new SeededRepository(repository, repository.getLocalPath(), headHash, temporaryRoot);
        }
        catch (Exception e) {
            if (repository != null) {
                repository.closeBeforeDelete();
            }
            deleteTemporaryCheckout(temporaryRoot);
            throw new IllegalStateException("Could not check out the " + repositoryType.name() + " repository for exercise " + exercise.getId(), e);
        }
    }

    private static void deleteTemporaryCheckout(@Nullable Path path) {
        if (path != null) {
            try {
                FileUtils.deleteDirectory(path.toFile());
            }
            catch (IOException e) {
                log.warn("Could not delete temporary Hyperion repository checkout {}: {}", path, e.getMessage());
            }
        }
    }

    private static void closeAndDeleteTemporaryCheckout(SeededRepository seededRepository) {
        seededRepository.repository().closeBeforeDelete();
        deleteTemporaryCheckout(seededRepository.temporaryRoot());
    }

    private record SeededRepository(Repository repository, Path workingTree, String headHash, Path temporaryRoot) {
    }

    /**
     * Maps a repository type to the stable workspace sub-directory name the generation workspace lays out on disk.
     *
     * @param repositoryType the repository type to place
     * @return the sub-directory name ({@code template}, {@code solution}, {@code tests}, or the lower-cased type name for any other value)
     */
    public static String directoryFor(RepositoryType repositoryType) {
        return switch (repositoryType) {
            case TEMPLATE -> "template";
            case SOLUTION -> "solution";
            case TESTS -> "tests";
            default -> repositoryType.name().toLowerCase(Locale.ROOT);
        };
    }
}
