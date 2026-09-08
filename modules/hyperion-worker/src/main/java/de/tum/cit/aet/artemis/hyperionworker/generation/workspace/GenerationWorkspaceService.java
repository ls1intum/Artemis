package de.tum.cit.aet.artemis.hyperionworker.generation.workspace;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief.Mode;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationInput;
import de.tum.cit.aet.artemis.hyperionworker.generation.GenerationResources;
import de.tum.cit.aet.artemis.hyperionworker.generation.RepositoryRole;
import de.tum.cit.aet.artemis.hyperionworker.generation.verification.ExerciseIntegrityGate;
import de.tum.cit.aet.artemis.hyperionworker.sandbox.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperionworker.sandbox.SandboxExecResult;

/**
 * Assembles the unified agent workspace (problem statement plus the template, solution, and test repositories, each in its own directory) into the sandbox and reads the produced
 * files back out, so the agent can make coherent cross-cutting changes across all components of an exercise.
 */
public class GenerationWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(GenerationWorkspaceService.class);

    public static final String WORKSPACE = "/workspace";

    private static final String PROBLEM_STATEMENT_FILE = "problem-statement.md";

    /** Re-seeded across session resets like the problem statement, so it survives the container restart each verification build performs. */
    private static final String SPEC_DOCUMENT_FILE = "SPEC.md";

    /** Re-seeded across session resets like the specification, or a repair attempt silently loses the grading weights and hidden tests. */
    private static final String TEST_PLAN_FILE = "test-plan.json";

    private static final RepositoryRole[] SEEDED_REPOSITORIES = { RepositoryRole.TEMPLATE, RepositoryRole.SOLUTION, RepositoryRole.TESTS };

    /** Must match {@link #directoryFor(RepositoryRole)}: the layout probe lists these directories and scans them for build manifests. */
    private static final String[] REPOSITORY_DIRECTORIES = { "solution", "template", "tests" };

    /**
     * The single bound on any sandbox command that does no compilation: reading a file, listing the layout, deleting build output, staging a fixture directory. Compilation is
     * bounded separately by the verification and build timeouts, so a command that has not returned within this means the sandbox is wedged, not that the work is slow.
     */
    public static final Duration SANDBOX_READ_TIMEOUT = Duration.ofSeconds(30);

    /** Upper bound on the turn-0 layout observation so a deeply nested tree cannot blow up the prompt. */
    private static final int LAYOUT_PROBE_MAX_CHARS = 6_000;

    /** Sandbox directory holding the worked-sample reference; never extracted or persisted. */
    static final String REFERENCE_DIR = "reference";

    /** The canonical Java exercise Artemis itself uses when it creates a programming exercise; Hyperion maintains no competing example. */
    private static final String JAVA_TEMPLATE_SOURCE_DIR = "java";

    private static final String JAVA_REFERENCE_PROJECT_SOURCE_DIR = JAVA_TEMPLATE_SOURCE_DIR + "/gradle_gradle";

    private static final String READINESS_SOURCE_DIR = "hyperion/readiness/java";

    /** Language-agnostic per-artifact style guides, seeded under {@code reference/style/} for every GENERATE run regardless of exercise language. */
    private static final String STYLE_GUIDE_SOURCE_DIR = "hyperion/style";

    private static final String REFERENCE_GUIDE = """
            # Worked exercise reference

            Study how the problem statement, starter, solution, tests, and task bindings fit together. Reuse Artemis and Ares conventions, not the exercise topic or design.
            Do not copy names, APIs, literal inputs, or implementation choices. Scale the design to the primary source requirements instead of treating this small example as a required shape.
            Before authoring, inspect the statement, compare template with solution, and then inspect the tests.
            Notice that the solution introduces a class and an interface that do not exist in the template at all, not just method bodies to fill in: when the primary source calls
            for student-created types, Artemis can support whole-type differences between template and solution. This is a capability demonstration, not a prescribed design:
            decide what students create from the primary source and accepted specification.
            """;

    /** Per-file and total caps on the seeded reference payload, so a large template cannot bloat the workspace tar. */
    private static final int MAX_REFERENCE_FILE_BYTES = 64_000;

    private static final int MAX_REFERENCE_TOTAL_BYTES = 512_000;

    private static final int MAX_STYLE_GUIDE_TOTAL_BYTES = 96_000;

    private final SandboxBuildCommandService sandboxBuildCommandService;

    private final GenerationResources resourceLoaderService;

    public GenerationWorkspaceService(SandboxBuildCommandService sandboxBuildCommandService, GenerationResources resourceLoaderService) {
        this.sandboxBuildCommandService = sandboxBuildCommandService;
        this.resourceLoaderService = resourceLoaderService;
    }

    /**
     * Seeds only the immutable input captured by core. No repository credentials or checkout paths reach the worker.
     * In GENERATE mode authored source roots are cleared, but harness bytes and executable modes remain authoritative.
     *
     * @param sandbox                the worker-local sandbox
     * @param sessionId              target sandbox session
     * @param exercise               immutable authoring facts
     * @param mode                   whether authored sources are cleared or retained
     * @param snapshot               repository files captured by core
     * @param statementAuthoritative whether the existing statement is instructor context rather than a default scaffold
     * @return seeded repository content and integrity metadata
     */
    public WorkspaceSeed seedWorkspace(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, Mode mode, WorkspaceSnapshot snapshot,
            boolean statementAuthoritative) {
        Map<RepositoryRole, RepositorySeedMetadata> metadata = new LinkedHashMap<>();
        Map<RepositoryRole, Map<String, String>> texts = new LinkedHashMap<>();
        Map<RepositoryRole, Map<String, BinarySeedFile>> binaries = new LinkedHashMap<>();
        for (RepositoryRole role : SEEDED_REPOSITORIES) {
            String prefix = directoryFor(role) + "/";
            Map<String, String> repositoryTexts = new LinkedHashMap<>();
            Map<String, BinarySeedFile> repositoryBinaries = new LinkedHashMap<>();
            Map<String, String> digests = new LinkedHashMap<>();
            Set<String> executableFiles = new LinkedHashSet<>();
            for (var file : snapshot.files()) {
                if (!file.path().startsWith(prefix)) {
                    continue;
                }
                String relative = file.path().substring(prefix.length());
                if (mode == Mode.GENERATE && isAuthoredSource(role, relative)) {
                    continue;
                }
                byte[] content = file.content();
                if (BinaryContent.isBinary(content)) {
                    repositoryBinaries.put(relative, new BinarySeedFile(content));
                    digests.put(relative, WorkspaceArchive.sha256(content));
                }
                else {
                    repositoryTexts.put(relative, new String(content, StandardCharsets.UTF_8));
                }
                if (file.executable()) {
                    executableFiles.add(relative);
                }
            }
            texts.put(role, Map.copyOf(repositoryTexts));
            binaries.put(role, Map.copyOf(repositoryBinaries));
            metadata.put(role, new RepositorySeedMetadata(Map.copyOf(digests), Set.copyOf(executableFiles)));
        }
        materializeRepositoryFiles(sandbox, sessionId, exercise, mode, texts, metadata, binaries,
                !statementAuthoritative || exercise.problemStatement() == null ? "" : exercise.problemStatement(), null, null);
        return new WorkspaceSeed(texts.get(RepositoryRole.TESTS), Map.copyOf(metadata), Map.copyOf(texts), Map.copyOf(binaries));
    }

    static boolean isAuthoredSource(RepositoryRole role, String path) {
        return role == RepositoryRole.TESTS ? path.startsWith("test/") || path.startsWith("behavior/test/") || path.startsWith("structural/test/") : path.startsWith("src/");
    }

    public record WorkspaceSeed(Map<String, String> testsSeedSnapshot, Map<RepositoryRole, RepositorySeedMetadata> repositoryMetadata,
            Map<RepositoryRole, Map<String, String>> repositoryTextFiles, Map<RepositoryRole, Map<String, BinarySeedFile>> repositoryBinaryFiles) {
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

    /** Returns the reference files keyed by archive-relative path under {@code reference/}, or empty if the set is incomplete. */
    Map<String, String> readReferenceSample(GenerationInput exercise) {
        Map<String, String> reference = new LinkedHashMap<>();
        int[] remainingBytes = { MAX_REFERENCE_TOTAL_BYTES - REFERENCE_GUIDE.getBytes(StandardCharsets.UTF_8).length };
        reference.put(REFERENCE_DIR + "/README.md", REFERENCE_GUIDE);
        addReferenceStatement(reference, remainingBytes);
        addReferenceArea(reference, JAVA_TEMPLATE_SOURCE_DIR + "/exercise", "template", path -> path.endsWith(".java"), remainingBytes);
        addReferenceArea(reference, JAVA_REFERENCE_PROJECT_SOURCE_DIR + "/exercise", "template", path -> path.endsWith(".java"), remainingBytes);
        addReferenceArea(reference, JAVA_TEMPLATE_SOURCE_DIR + "/solution", "solution", path -> path.endsWith(".java"), remainingBytes);
        addReferenceArea(reference, JAVA_REFERENCE_PROJECT_SOURCE_DIR + "/solution", "solution", path -> path.endsWith(".java"), remainingBytes);
        addReferenceArea(reference, JAVA_TEMPLATE_SOURCE_DIR + "/test/testFiles/behavior", "tests/behavior", path -> path.endsWith(".java"), remainingBytes);
        addReferenceArea(reference, JAVA_TEMPLATE_SOURCE_DIR + "/test/testFiles/structural", "tests/structural", path -> path.endsWith(".java") || path.endsWith(".json"),
                remainingBytes);
        boolean complete = reference.containsKey(REFERENCE_DIR + "/problem-statement.md") && hasReferenceArea(reference, "template") && hasReferenceArea(reference, "solution")
                && hasReferenceArea(reference, "tests/behavior") && hasReferenceArea(reference, "tests/structural");
        return complete ? reference : Map.of();
    }

    /** Unlike {@link #readReferenceSample} this is not gated on a language: the guides are topic-neutral prose, not source, so every GENERATE run gets them. */
    Map<String, String> readStyleGuides() {
        Map<String, String> guides = new LinkedHashMap<>();
        int[] remainingBytes = { MAX_STYLE_GUIDE_TOTAL_BYTES };
        addReferenceArea(guides, STYLE_GUIDE_SOURCE_DIR, "style", path -> path.endsWith(".md"), remainingBytes);
        return guides;
    }

    Map<String, String> readBuildReadinessFixture(GenerationInput exercise) {
        Map<String, String> sources = new LinkedHashMap<>();
        int[] remainingBytes = { MAX_REFERENCE_TOTAL_BYTES };
        addReferenceArea(sources, READINESS_SOURCE_DIR + "/solution", "solution", path -> path.endsWith(".java"), remainingBytes);
        addReferenceArea(sources, READINESS_SOURCE_DIR + "/tests/behavior", "behavior", path -> path.endsWith(".java"), remainingBytes);
        addReferenceArea(sources, READINESS_SOURCE_DIR + "/tests/structural", "structural", path -> path.endsWith(".java"), remainingBytes);
        Map<String, String> fixture = new LinkedHashMap<>();
        sources.forEach((path, content) -> {
            if (path.startsWith(REFERENCE_DIR + "/solution/")) {
                fixture.put(path.substring((REFERENCE_DIR + "/").length()), content);
            }
            else if (path.startsWith(REFERENCE_DIR + "/behavior/")) {
                String relativeTestPath = path.substring((REFERENCE_DIR + "/behavior/").length());
                fixture.put("tests/test/" + relativeTestPath, content);
            }
            else if (path.startsWith(REFERENCE_DIR + "/structural/")) {
                String relativeTestPath = path.substring((REFERENCE_DIR + "/structural/").length());
                fixture.put("tests/test/" + relativeTestPath, content);
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
    public void stageBuildReadinessFixture(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise) {
        SandboxExecResult preparation = sandbox.exec(sessionId, SANDBOX_READ_TIMEOUT, "sh", "-c",
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
        Resource resource = resourceLoaderService.getResource(Path.of("templates", JAVA_REFERENCE_PROJECT_SOURCE_DIR, "readme"));
        String content = readReferenceResource(resource, remainingBytes);
        if (content != null) {
            reference.put(REFERENCE_DIR + "/problem-statement.md", GenerationResources.javaGradleStatementFixture(content));
        }
    }

    /** The relative path is recovered from a URI marker rather than the resource path so the same code works for filesystem and jar resources. */
    private void addReferenceArea(Map<String, String> reference, String sourceArea, String targetArea, Predicate<String> include, int[] remainingBytes) {
        String marker = "/templates/" + sourceArea + "/";
        Resource[] resources = resourceLoaderService.getFileResources(Path.of("templates").resolve(sourceArea));
        for (Resource resource : resources) {
            if (remainingBytes[0] <= 0) {
                return;
            }
            try {
                String uri = URLDecoder.decode(resource.getURI().toString(), StandardCharsets.UTF_8).replace('\\', '/');
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
            return GenerationResources.javaGradleFixture(new String(content, StandardCharsets.UTF_8));
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
        String script = "cd " + WORKSPACE + " 2>/dev/null || exit 0\n" + "echo '--- ls -R " + String.join(" ", REPOSITORY_DIRECTORIES) + " ---'\n" + "ls -R "
                + String.join(" ", REPOSITORY_DIRECTORIES) + " 2>/dev/null\n" + "for f in $(find " + String.join(" ", REPOSITORY_DIRECTORIES)
                + " -maxdepth 2 -type f \\( -name pom.xml -o -name build.gradle -o -name build.gradle.kts -o -name settings.gradle -o -name settings.gradle.kts -o -name gradle.properties \\) 2>/dev/null | sort); do\n"
                + "  echo; echo \"--- head -40 $f ---\"; head -40 \"$f\" 2>/dev/null\n" + "done\n"
                // Surface the reference dir so the agent discovers it (it is not a repository dir, so the listing above misses it).
                + "if [ -d " + REFERENCE_DIR + " ]; then echo; echo '--- ls -R " + REFERENCE_DIR
                + " (non-persisted worked example: study its language and test-framework conventions; do not edit or copy it) ---'; ls -R " + REFERENCE_DIR
                + " 2>/dev/null | head -c 1500; fi\n";
        try {
            SandboxExecResult result = sandbox.exec(sessionId, SANDBOX_READ_TIMEOUT, "sh", "-c", script);
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

    private static String truncateLayout(String layout) {
        if (layout.length() <= LAYOUT_PROBE_MAX_CHARS) {
            return layout;
        }
        return layout.substring(0, LAYOUT_PROBE_MAX_CHARS) + "\n… [workspace layout truncated; list deeper directories yourself with `ls -R` if you need more]";
    }

    /**
     * The produced files of a repository read back out of the sandbox. The verifier fails closed when {@code extractionFailed} is true.
     *
     * @param files            the produced files keyed by repository-relative path; an empty map says nothing about success and a failed read-back can still carry files (residue
     *                             stripped, binaries changed), so only {@code extractionFailed} decides
     * @param extractionFailed {@code true} when extraction failed or the produced tree contains unsupported residue or binary changes
     */
    public record RepositoryExtraction(Map<String, String> files, boolean extractionFailed) {
    }

    /**
     * Overwrites repository text files with the canonical bytes that verification and persistence must share, and re-seeds the workspace-root files alongside them. Callers use
     * this to restore a candidate into a just-reset session, and {@code /workspace} is a bounded tmpfs that the container restart empties completely, so a restore touching only
     * the three repositories would silently drop everything living at the workspace root.
     *
     * @param sandbox               the sandbox session
     * @param sessionId             the session handle
     * @param filesByRepository     the canonical repository text files
     * @param repositoryMetadata    seeded file metadata used to preserve executable modes
     * @param repositoryBinaryFiles the canonical repository binary files, written back verbatim alongside the text files
     * @param exercise              the exercise whose workspace-root bootstrap files ({@code verify.sh}, and for GENERATE the {@code reference/} sample and style guides) are
     *                                  re-seeded
     * @param mode                  the generation mode the workspace was originally seeded for
     * @param problemStatement      the canonical problem statement to re-seed at the workspace root, or {@code null} to leave it untouched
     * @param testPlanJson          the TESTS stage's {@code test-plan.json} to re-seed at the workspace root, or {@code null} to leave it untouched
     * @param specDocument          the agent's {@code SPEC.md} to re-seed at the workspace root, or {@code null} to leave it untouched
     */
    public void materializeRepositoryFiles(InteractiveSandbox sandbox, String sessionId, GenerationInput exercise, Mode mode,
            Map<RepositoryRole, Map<String, String>> filesByRepository, Map<RepositoryRole, RepositorySeedMetadata> repositoryMetadata,
            Map<RepositoryRole, Map<String, BinarySeedFile>> repositoryBinaryFiles, @Nullable String problemStatement, @Nullable String specDocument,
            @Nullable String testPlanJson) {
        Map<String, String> workspaceFiles = new LinkedHashMap<>();
        String verifyScript = sandboxBuildCommandService.verifyScriptContent(exercise);
        if (verifyScript != null) {
            workspaceFiles.put(SandboxBuildCommandService.VERIFY_SCRIPT_NAME, verifyScript);
        }
        if (mode == Mode.GENERATE) {
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
        if (testPlanJson != null) {
            workspaceFiles.put(TEST_PLAN_FILE, testPlanJson);
        }
        if (specDocument != null) {
            workspaceFiles.put(SPEC_DOCUMENT_FILE, specDocument);
        }
        sandbox.copyIn(sessionId, WORKSPACE, WorkspaceArchive.buildFilesTarStream(workspaceFiles, workspaceBinaryFiles, executableFiles));
    }

    /**
     * Removes disposable build outputs that raw debugging commands may have left inside the seeded repositories, so canonical extraction does not persist them.
     *
     * @param sandbox   the sandbox session
     * @param sessionId the session handle
     */
    public void cleanTransientBuildOutputs(InteractiveSandbox sandbox, String sessionId) {
        String command = "rm -rf -- " + WORKSPACE + "/solution/.gradle " + WORKSPACE + "/solution/build " + WORKSPACE + "/solution/target " + WORKSPACE
                + "/solution/buildSrc/.gradle " + WORKSPACE + "/solution/buildSrc/build " + WORKSPACE + "/template/.gradle " + WORKSPACE + "/template/build " + WORKSPACE
                + "/template/target " + WORKSPACE + "/template/buildSrc/.gradle " + WORKSPACE + "/template/buildSrc/build " + WORKSPACE + "/tests/.gradle " + WORKSPACE
                + "/tests/build " + WORKSPACE + "/tests/target " + WORKSPACE + "/tests/buildSrc/.gradle " + WORKSPACE + "/tests/buildSrc/build";
        SandboxExecResult result = sandbox.exec(sessionId, SANDBOX_READ_TIMEOUT, "sh", "-c", command);
        if (!result.isSuccess()) {
            throw new IllegalStateException("Could not remove transient sandbox build outputs: " + result.combinedOutput());
        }
    }

    public Map<String, String> extractRepositoryFiles(InteractiveSandbox sandbox, String sessionId, RepositoryRole repositoryType) {
        return extractRepository(sandbox, sessionId, repositoryType, null).files();
    }

    /**
     * Reads the produced files back out through the tar API rather than per-file shell reads, which would truncate anything over the exec output-capture limit.
     *
     * @param sandbox          the sandbox session
     * @param sessionId        the session handle
     * @param repositoryType   the repository whose files to read back
     * @param expectedMetadata the seeded binary digests and executable paths, or {@code null} for an advisory text-only read
     * @return the produced files and an extraction-failed flag
     */
    public RepositoryExtraction extractRepository(InteractiveSandbox sandbox, String sessionId, RepositoryRole repositoryType, RepositorySeedMetadata expectedMetadata) {
        String dir = directoryFor(repositoryType);
        try (TarArchiveInputStream tar = sandbox.copyOut(sessionId, WORKSPACE + "/" + dir)) {
            // Docker prefixes copied-out entries with the source directory's own name.
            WorkspaceArchive.ArchiveContents contents = WorkspaceArchive.readTarContents(tar, dir);
            Map<String, String> files = contents.textFiles();
            if (expectedMetadata != null
                    && (!expectedMetadata.binaryDigests().equals(contents.binaryDigests()) || !expectedMetadata.executableFiles().equals(contents.executableFiles()))) {
                return new RepositoryExtraction(files, true);
            }
            if (repositoryType == RepositoryRole.TEMPLATE || repositoryType == RepositoryRole.SOLUTION) {
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
     * @param sandbox   the sandbox session
     * @param sessionId the session handle
     * @return the produced problem statement
     * @throws IllegalStateException if it is missing or unreadable, so an empty statement can never be persisted as if it had been authored
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

    /**
     * The workspace layout contract: every prompt, the verify script, and repository extraction resolve a repository through this one mapping.
     *
     * @param repositoryType the repository type to place
     * @return the sub-directory name
     */
    public static String directoryFor(RepositoryRole repositoryType) {
        return switch (repositoryType) {
            case TEMPLATE -> "template";
            case SOLUTION -> "solution";
            case TESTS -> "tests";
            default -> repositoryType.name().toLowerCase(Locale.ROOT);
        };
    }
}
