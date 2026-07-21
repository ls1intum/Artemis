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
import java.util.function.Predicate;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

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
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
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

    /** The agent's workspace-root design note (see GenerationStage#DESIGN); re-seeded across session resets like the problem statement so it survives verification builds. */
    private static final String DESIGN_DOCUMENT_FILE = "DESIGN.md";

    private static final RepositoryType[] SEEDED_REPOSITORIES = { RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS };

    /** Repository directories the layout probe lists and scans for build manifests; matches {@link #directoryFor(RepositoryType)}. */
    private static final String[] REPOSITORY_DIRECTORIES = { "solution", "template", "tests" };

    private static final Duration LAYOUT_PROBE_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration BUILD_OUTPUT_CLEANUP_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration FIXTURE_STAGE_TIMEOUT = Duration.ofSeconds(30);

    /** Upper bound on the turn-0 layout observation so a deeply nested tree cannot blow up the prompt. */
    private static final int LAYOUT_PROBE_MAX_CHARS = 6_000;

    /** Sandbox directory holding the worked-sample reference; never extracted or persisted. */
    static final String REFERENCE_DIR = "reference";

    private static final String REFERENCE_SOURCE_DIR = "hyperion/reference/java";

    private static final String READINESS_SOURCE_DIR = "hyperion/readiness/java";

    /** Classpath directory of language-agnostic per-artifact style guides, seeded under {@code reference/style/} for every GENERATE run regardless of exercise language. */
    private static final String STYLE_GUIDE_SOURCE_DIR = "hyperion/style";

    private static final String REFERENCE_GUIDE = """
            # Worked exercise reference

            Study how the problem statement, starter, solution, tests, and task bindings fit together. Reuse Artemis and Ares conventions, not the exercise topic or design.
            Do not copy names, APIs, literal inputs, or implementation choices. Scale the design to the primary source requirements instead of treating this small example as a required shape.
            Before authoring, inspect the statement, compare template with solution, and then inspect the tests.
            Notice that the solution introduces a class and an interface that do not exist in the template at all, not just method bodies to fill in: when the primary source calls
            for multiple collaborating types (a strategy, a pattern, a small class hierarchy), give the template only the pieces students implement directly and let the solution
            introduce the rest, rather than collapsing every exercise into a single class with one method.
            """;

    /** Per-file and total caps on the seeded reference payload, so a large template cannot bloat the workspace tar. */
    private static final int MAX_REFERENCE_FILE_BYTES = 64_000;

    private static final int MAX_REFERENCE_TOTAL_BYTES = 512_000;

    /** Total cap on the seeded style-guide payload; the guides are tiny prose files, so this stays far below {@link #MAX_REFERENCE_TOTAL_BYTES}. */
    private static final int MAX_STYLE_GUIDE_TOTAL_BYTES = 96_000;

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
        return seedWorkspace(sandbox, sessionId, exercise, mode, true);
    }

    /**
     * Like {@link #seedWorkspace(InteractiveSandbox, String, ProgrammingExercise, GenerationMode)}, with control over whether the exercise's problem statement is seeded.
     *
     * @param sandbox                the sandbox session
     * @param sessionId              the session handle
     * @param exercise               the exercise whose components are seeded
     * @param mode                   whether to start from clean exercise artifacts or preserve the existing tree
     * @param statementAuthoritative whether {@code exercise.getProblemStatement()} is a real instructor specification; {@code false} (a blank field or the client-seeded
     *                                   default template readme) seeds an EMPTY problem-statement.md, so the agent never mistakes the default sorting readme for the spec
     * @return the seeded repository heads plus TESTS-repo text files used later by the immutability and stale-head gates
     */
    public WorkspaceSeed seedWorkspace(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, GenerationMode mode, boolean statementAuthoritative) {
        String defaultBranch = exercise.getBuildConfig() != null ? exercise.getBuildConfig().getBranch() : null;
        Map<String, String> textFiles = new LinkedHashMap<>();
        textFiles.put(PROBLEM_STATEMENT_FILE, !statementAuthoritative || exercise.getProblemStatement() == null ? "" : exercise.getProblemStatement());
        textFiles.put(SandboxBuildCommandService.VERIFY_SCRIPT_NAME, sandboxBuildCommandService.verifyScriptContent(exercise));
        Map<String, Path> repositoryTrees = new LinkedHashMap<>();
        Map<RepositoryType, String> repositoryHeads = new LinkedHashMap<>();
        Map<RepositoryType, RepositorySeedMetadata> repositoryMetadata = new LinkedHashMap<>();
        Map<RepositoryType, Map<String, String>> repositoryTextFiles = new LinkedHashMap<>();
        Map<RepositoryType, Map<String, BinarySeedFile>> repositoryBinaryFiles = new LinkedHashMap<>();
        Map<String, String> testsSeedSnapshot = Map.of();
        List<SeededRepository> temporaryCheckouts = new ArrayList<>();
        try {
            for (RepositoryType repositoryType : SEEDED_REPOSITORIES) {
                SeededRepository seededRepository = checkoutWorkingTree(exercise, repositoryType, defaultBranch);
                temporaryCheckouts.add(seededRepository);
                prepareRepositoryForMode(seededRepository.workingTree(), repositoryType, mode);
                repositoryTrees.put(directoryFor(repositoryType), seededRepository.workingTree());
                repositoryHeads.put(repositoryType, seededRepository.headHash());
                RepositorySeedContent seedContent = readWorkingTreeSeedContent(seededRepository.workingTree());
                repositoryMetadata.put(repositoryType, seedContent.metadata());
                repositoryBinaryFiles.put(repositoryType, seedContent.binaryFiles());
                Map<String, String> textSnapshot = Map.copyOf(readWorkingTreeTextFiles(seededRepository.workingTree()));
                repositoryTextFiles.put(repositoryType, textSnapshot);
                if (repositoryType == RepositoryType.TESTS) {
                    testsSeedSnapshot = textSnapshot;
                }
            }
            Map<String, String> referenceSample = mode == GenerationMode.GENERATE ? readReferenceSample(exercise) : Map.of();
            Map<String, String> styleGuides = mode == GenerationMode.GENERATE ? readStyleGuides() : Map.of();
            textFiles.putAll(referenceSample);
            textFiles.putAll(styleGuides);
            sandbox.copyIn(sessionId, WORKSPACE, WorkspaceArchive.buildWorkspaceTarStream(textFiles, repositoryTrees));
            log.info("Seeded generation workspace for exercise {} ({} repositories, {} reference files, {} style guides)", exercise.getId(), repositoryTrees.size(),
                    referenceSample.size(), styleGuides.size());
            return new WorkspaceSeed(testsSeedSnapshot, Map.copyOf(repositoryHeads), Map.copyOf(repositoryMetadata), Map.copyOf(repositoryTextFiles),
                    Map.copyOf(repositoryBinaryFiles));
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
            Map<RepositoryType, Map<String, String>> repositoryTextFiles, Map<RepositoryType, Map<String, BinarySeedFile>> repositoryBinaryFiles) {

        public WorkspaceSeed(Map<String, String> testsSeedSnapshot, Map<RepositoryType, String> repositoryHeads) {
            this(testsSeedSnapshot, repositoryHeads, Map.of(), Map.of(), Map.of());
        }

        public WorkspaceSeed(Map<String, String> testsSeedSnapshot, Map<RepositoryType, String> repositoryHeads, Map<RepositoryType, RepositorySeedMetadata> repositoryMetadata) {
            this(testsSeedSnapshot, repositoryHeads, repositoryMetadata, Map.of(), Map.of());
        }

        public WorkspaceSeed(Map<String, String> testsSeedSnapshot, Map<RepositoryType, String> repositoryHeads, Map<RepositoryType, RepositorySeedMetadata> repositoryMetadata,
                Map<RepositoryType, Map<String, String>> repositoryTextFiles) {
            this(testsSeedSnapshot, repositoryHeads, repositoryMetadata, repositoryTextFiles, Map.of());
        }
    }

    public record RepositorySeedMetadata(Map<String, String> binaryDigests, Set<String> executableFiles) {

        public static final RepositorySeedMetadata EMPTY = new RepositorySeedMetadata(Map.of(), Set.of());
    }

    public record BinarySeedFile(byte[] content) {

        public BinarySeedFile {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    private record RepositorySeedContent(RepositorySeedMetadata metadata, Map<String, BinarySeedFile> binaryFiles) {
    }

    /**
     * Reads one compact, complete Java worked example from the classpath templates. The reference includes its statement, starter, solution, and behavioral tests, but excludes
     * redundant structural tests, build manifests, and generic harness implementation.
     *
     * @param exercise the exercise whose language selects the reference
     * @return the reference files keyed by their archive-relative path under {@code reference/}, or empty if none could be read
     */
    Map<String, String> readReferenceSample(ProgrammingExercise exercise) {
        if (exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            return Map.of();
        }
        Map<String, String> reference = new LinkedHashMap<>();
        int[] remainingBytes = { MAX_REFERENCE_TOTAL_BYTES - REFERENCE_GUIDE.getBytes(StandardCharsets.UTF_8).length };
        reference.put(REFERENCE_DIR + "/README.md", REFERENCE_GUIDE);
        addReferenceStatement(reference, remainingBytes);
        addReferenceArea(reference, REFERENCE_SOURCE_DIR + "/template", "template", path -> path.endsWith(".java"), remainingBytes);
        addReferenceArea(reference, REFERENCE_SOURCE_DIR + "/solution", "solution", path -> path.endsWith(".java"), remainingBytes);
        addReferenceArea(reference, REFERENCE_SOURCE_DIR + "/tests/test", "tests/test", path -> path.endsWith(".java"), remainingBytes);
        boolean complete = reference.containsKey(REFERENCE_DIR + "/problem-statement.md") && hasReferenceArea(reference, "template") && hasReferenceArea(reference, "solution")
                && hasReferenceArea(reference, "tests/test");
        return complete ? reference : Map.of();
    }

    /**
     * Reads the language-agnostic per-artifact style guides (draft statement, final statement, template, solution, tests) from the classpath. Unlike {@link #readReferenceSample},
     * this is not gated on Java: the guides are prose principles plus a small neutral exemplar, not language-specific source, so every GENERATE run benefits from them.
     *
     * @return the style guide files keyed by their archive-relative path under {@code reference/style/}, or empty if none could be read
     */
    Map<String, String> readStyleGuides() {
        Map<String, String> guides = new LinkedHashMap<>();
        int[] remainingBytes = { MAX_STYLE_GUIDE_TOTAL_BYTES };
        addReferenceArea(guides, STYLE_GUIDE_SOURCE_DIR, "style", path -> path.endsWith(".md"), remainingBytes);
        return guides;
    }

    Map<String, String> readBuildReadinessFixture(ProgrammingExercise exercise) {
        if (exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            return Map.of();
        }
        Map<String, String> sources = new LinkedHashMap<>();
        int[] remainingBytes = { MAX_REFERENCE_TOTAL_BYTES };
        addReferenceArea(sources, READINESS_SOURCE_DIR + "/solution", "solution", path -> path.endsWith(".java"), remainingBytes);
        addReferenceArea(sources, READINESS_SOURCE_DIR + "/tests/behavior", "behavior", path -> path.endsWith(".java"), remainingBytes);
        addReferenceArea(sources, READINESS_SOURCE_DIR + "/tests/structural", "structural", path -> path.endsWith(".java"), remainingBytes);
        boolean sequential = exercise.getBuildConfig() != null && exercise.getBuildConfig().hasSequentialTestRuns();
        Map<String, String> fixture = new LinkedHashMap<>();
        sources.forEach((path, content) -> {
            if (path.startsWith(REFERENCE_DIR + "/solution/")) {
                fixture.put(path.substring((REFERENCE_DIR + "/").length()), content);
            }
            else if (path.startsWith(REFERENCE_DIR + "/behavior/")) {
                String relativeTestPath = path.substring((REFERENCE_DIR + "/behavior/").length());
                fixture.put(sequential ? "tests/behavior/test/" + relativeTestPath : "tests/test/" + relativeTestPath, content);
            }
            else if (path.startsWith(REFERENCE_DIR + "/structural/")) {
                String relativeTestPath = path.substring((REFERENCE_DIR + "/structural/").length());
                fixture.put(sequential ? "tests/structural/test/" + relativeTestPath : "tests/test/" + relativeTestPath, content);
            }
        });
        return fixture;
    }

    /**
     * Stages the server-owned fixture immediately before the pre-provider readiness build. The readiness script removes it before the agent starts.
     *
     * @param sandbox   the sandbox receiving the fixture
     * @param sessionId the target sandbox session
     * @param exercise  the exercise whose build layout the fixture must match
     */
    public void stageBuildReadinessFixture(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise) {
        SandboxExecResult preparation = sandbox.exec(sessionId, FIXTURE_STAGE_TIMEOUT, "sh", "-c",
                "find " + SandboxBuildCommandService.READINESS_FIXTURE_DIR + " -mindepth 1 -delete");
        if (!preparation.isSuccess()) {
            throw new IllegalStateException("Could not prepare the build-readiness fixture directory: " + preparation.combinedOutput());
        }
        sandbox.copyIn(sessionId, SandboxBuildCommandService.READINESS_FIXTURE_DIR, WorkspaceArchive.buildWorkspaceTarStream(readBuildReadinessFixture(exercise), Map.of()));
    }

    private static boolean hasReferenceArea(Map<String, String> reference, String area) {
        String prefix = REFERENCE_DIR + "/" + area + "/";
        return reference.keySet().stream().anyMatch(path -> path.startsWith(prefix));
    }

    private void addReferenceStatement(Map<String, String> reference, int[] remainingBytes) {
        Resource resource = resourceLoaderService.getResource(Path.of("templates", REFERENCE_SOURCE_DIR, "problem-statement.md"));
        String content = readReferenceResource(resource, remainingBytes);
        if (content != null) {
            reference.put(REFERENCE_DIR + "/problem-statement.md", content);
        }
    }

    /**
     * Adds the readable text files under {@code templates/<languageRelativeBase>/<area>} to {@code reference}, keyed {@code reference/<area>/<rest>} (the path relative to the
     * language
     * template root), respecting the remaining byte budget. Robust across filesystem and jar resources via the {@code /templates/<languageRelativeBase>/} URI marker.
     */
    private void addReferenceArea(Map<String, String> reference, String sourceArea, String targetArea, Predicate<String> include, int[] remainingBytes) {
        String marker = "/templates/" + sourceArea + "/";
        Resource[] resources = resourceLoaderService.getFileResources(Path.of("templates").resolve(sourceArea));
        for (Resource resource : resources) {
            if (remainingBytes[0] <= 0) {
                return;
            }
            try {
                String uri = UriUtils.decode(resource.getURI().toString(), StandardCharsets.UTF_8).replace('\\', '/');
                int markerIndex = uri.indexOf(marker);
                if (markerIndex < 0) {
                    continue;
                }
                String relativePath = normalizeReferencePath(uri.substring(markerIndex + marker.length()));
                if (relativePath == null || !include.test(relativePath)) {
                    continue;
                }
                String content = readReferenceResource(resource, remainingBytes);
                if (content != null) {
                    reference.put(REFERENCE_DIR + "/" + targetArea + "/" + relativePath, content);
                }
            }
            catch (IOException | RuntimeException e) {
                log.debug("Skipping reference sample resource {}: {}", resource, e.getMessage());
            }
        }
    }

    private static @Nullable String normalizeReferencePath(String relativePath) {
        try {
            Path path = Path.of(relativePath);
            if (path.isAbsolute() || relativePath.indexOf('\\') >= 0) {
                return null;
            }
            for (Path segment : path) {
                if (segment.toString().equals("..")) {
                    return null;
                }
            }
            String normalized = path.normalize().toString().replace('\\', '/');
            return normalized.isEmpty() ? null : normalized;
        }
        catch (RuntimeException e) {
            return null;
        }
    }

    private @Nullable String readReferenceResource(Resource resource, int[] remainingBytes) {
        int maxBytes = Math.min(MAX_REFERENCE_FILE_BYTES, remainingBytes[0]);
        if (maxBytes <= 0) {
            return null;
        }
        try (var input = resource.getInputStream()) {
            byte[] content = input.readNBytes(maxBytes + 1);
            if (content.length == 0 || content.length > maxBytes || BinaryContent.isBinary(content)) {
                return null;
            }
            remainingBytes[0] -= content.length;
            return new String(content, StandardCharsets.UTF_8);
        }
        catch (IOException | RuntimeException e) {
            log.debug("Skipping reference sample resource {}: {}", resource, e.getMessage());
            return null;
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
        // One shell pass: ls -R the repo dirs, then find+head the build manifest present at their roots. Generation only ever runs for Java/Maven exercises
        // (LanguageGenerationProfile), so only pom.xml is probed.
        String script = "cd " + WORKSPACE + " 2>/dev/null || exit 0\n" + "echo '--- ls -R " + String.join(" ", REPOSITORY_DIRECTORIES) + " ---'\n" + "ls -R "
                + String.join(" ", REPOSITORY_DIRECTORIES) + " 2>/dev/null\n" + "for f in $(find " + String.join(" ", REPOSITORY_DIRECTORIES)
                + " -maxdepth 2 -type f -name pom.xml 2>/dev/null | sort); do\n" + "  echo; echo \"--- head -40 $f ---\"; head -40 \"$f\" 2>/dev/null\n" + "done\n"
                // Surface the reference dir so the agent discovers it (it is not a repository dir, so the listing above misses it).
                + "if [ -d " + REFERENCE_DIR + " ]; then echo; echo '--- ls -R " + REFERENCE_DIR
                + " (non-persisted worked example: study its language and test-framework conventions; do not edit or copy it) ---'; ls -R " + REFERENCE_DIR
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

    private static RepositorySeedContent readWorkingTreeSeedContent(Path workingTree) {
        Map<String, String> digests = new LinkedHashMap<>();
        Map<String, BinarySeedFile> binaryFiles = new LinkedHashMap<>();
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
                    binaryFiles.put(relative, new BinarySeedFile(content));
                }
            }
        }
        catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not fingerprint the seeded repository binaries", e);
        }
        return new RepositorySeedContent(new RepositorySeedMetadata(Map.copyOf(digests), Set.copyOf(executableFiles)), Map.copyOf(binaryFiles));
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
     * <p>
     * Also re-seeds the workspace-root problem statement when given. This matters because callers use this method to restore a candidate into a sandbox session that was just
     * {@code resetSession}'d: {@code /workspace} is mounted on a bounded tmpfs (see {@code InteractiveSandboxService#hardenedHostConfig}), so a container restart empties it
     * completely — a candidate restore that touched only the three repositories would silently drop {@code problem-statement.md} (it lives at the workspace root, outside any
     * repository), and the next read of it would fail with "the generated problem statement is missing".
     *
     * @param sandbox               the sandbox session
     * @param sessionId             the session handle
     * @param filesByRepository     the canonical repository text files
     * @param repositoryMetadata    seeded file metadata used to preserve executable modes
     * @param repositoryBinaryFiles the canonical repository binary files, written back verbatim alongside the text files
     * @param exercise              the exercise whose workspace-root bootstrap files ({@code verify.sh}, and for GENERATE the {@code reference/} sample and
     *                                  {@code reference/style/} guides) are re-seeded alongside the repositories — the session reset wipes the whole tmpfs workspace, and a
     *                                  restore that reproduces only a subset of the bootstrap leaves later attempts chasing files the prompts promise exist
     * @param mode                  the generation mode the workspace was originally seeded for
     * @param problemStatement      the canonical problem statement to re-seed at the workspace root, or {@code null} to leave it untouched
     * @param designDocument        the agent's {@code DESIGN.md} working memory to re-seed at the workspace root, or {@code null} to leave it untouched; without this, the
     *                                  session reset before each pristine verification build silently discards it — repair attempts lose the design rationale and the outcome's
     *                                  design-document capture reads nothing
     */
    public void materializeRepositoryFiles(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, GenerationMode mode,
            Map<RepositoryType, Map<String, String>> filesByRepository, Map<RepositoryType, RepositorySeedMetadata> repositoryMetadata,
            Map<RepositoryType, Map<String, BinarySeedFile>> repositoryBinaryFiles, @Nullable String problemStatement, @Nullable String designDocument) {
        Map<String, String> workspaceFiles = new LinkedHashMap<>();
        String verifyScript = sandboxBuildCommandService.verifyScriptContent(exercise);
        if (verifyScript != null) {
            workspaceFiles.put(SandboxBuildCommandService.VERIFY_SCRIPT_NAME, verifyScript);
        }
        if (mode == GenerationMode.GENERATE) {
            workspaceFiles.putAll(readReferenceSample(exercise));
            workspaceFiles.putAll(readStyleGuides());
        }
        Map<String, byte[]> workspaceBinaryFiles = new LinkedHashMap<>();
        Set<String> executableFiles = new LinkedHashSet<>();
        filesByRepository.forEach((repositoryType, files) -> files.forEach((path, content) -> workspaceFiles.put(directoryFor(repositoryType) + "/" + path, content)));
        repositoryBinaryFiles
                .forEach((repositoryType, files) -> files.forEach((path, file) -> workspaceBinaryFiles.put(directoryFor(repositoryType) + "/" + path, file.content())));
        repositoryMetadata.forEach((repositoryType, metadata) -> metadata.executableFiles().forEach(path -> executableFiles.add(directoryFor(repositoryType) + "/" + path)));
        if (problemStatement != null) {
            workspaceFiles.put(PROBLEM_STATEMENT_FILE, problemStatement);
        }
        if (designDocument != null) {
            workspaceFiles.put(DESIGN_DOCUMENT_FILE, designDocument);
        }
        sandbox.copyIn(sessionId, WORKSPACE, WorkspaceArchive.buildFilesTarStream(workspaceFiles, workspaceBinaryFiles, executableFiles));
    }

    /**
     * Removes disposable build outputs that raw debugging commands may have left inside the seeded repositories before canonical extraction.
     *
     * @param sandbox   the sandbox session
     * @param sessionId the session handle
     */
    public void cleanTransientBuildOutputs(InteractiveSandbox sandbox, String sessionId) {
        String command = "rm -rf -- " + WORKSPACE + "/solution/.gradle " + WORKSPACE + "/solution/build " + WORKSPACE + "/solution/target " + WORKSPACE
                + "/solution/buildSrc/.gradle " + WORKSPACE + "/solution/buildSrc/build " + WORKSPACE + "/template/.gradle " + WORKSPACE + "/template/build " + WORKSPACE
                + "/template/target " + WORKSPACE + "/template/buildSrc/.gradle " + WORKSPACE + "/template/buildSrc/build " + WORKSPACE + "/tests/.gradle " + WORKSPACE
                + "/tests/build " + WORKSPACE + "/tests/target " + WORKSPACE + "/tests/buildSrc/.gradle " + WORKSPACE + "/tests/buildSrc/build";
        SandboxExecResult result = sandbox.exec(sessionId, BUILD_OUTPUT_CLEANUP_TIMEOUT, "sh", "-c", command);
        if (!result.isSuccess()) {
            throw new IllegalStateException("Could not remove transient sandbox build outputs: " + result.combinedOutput());
        }
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
