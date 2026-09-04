package de.tum.cit.aet.artemis.hyperion.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Renders a {@link ProgrammingExercise} — its problem statement plus its template and solution repositories — as the plain text a prompt can reason over.
 * <p>
 * The prompts ask models to answer with file paths and line numbers, which only works because every file is announced by its path and numbered from 1. A repository is preceded by
 * its tree, with hidden paths left out. The output is deterministic for a given repository state: paths are sorted, and a repository that cannot be read becomes an empty section
 * rather than an error.
 *
 * <pre>
 * ===== Problem Statement =====
 * --------------------------------------------------------------------------------
 * problem_statement.md:
 * --------------------------------------------------------------------------------
 * 1 | # Implement a Stack
 *
 * ===== Template Repository =====
 * template_repository
 * └── src
 *     └── Stack.java
 *
 * --------------------------------------------------------------------------------
 * template_repository/src/Stack.java:
 * --------------------------------------------------------------------------------
 *  1 | public class Stack { }
 * </pre>
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionProgrammingExerciseContextRendererService {

    private static final Logger log = LoggerFactory.getLogger(HyperionProgrammingExerciseContextRendererService.class);

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(".git", ".idea", ".vscode", "target", "build", "out", "bin", "node_modules", ".gradle", ".mvn", "dist", ".next",
            "coverage");

    private static final Set<String> EXCLUDED_FILES = Set.of(".DS_Store", "Thumbs.db", ".gitkeep");

    private static final Set<String> BUILD_ENVIRONMENT_FILES = Set.of("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties",
            "requirements.txt", "pyproject.toml", "setup.py", "Pipfile", "Pipfile.lock", "CMakeLists.txt", "Makefile", "Package.swift", "Cargo.toml", "Gemfile", "package.json",
            ".java-version", ".tool-versions");

    private static final int MAX_BUILD_ENVIRONMENT_FILE_CONTENT_LENGTH = 4000;

    private final RepositoryService repositoryService;

    private final HyperionProgrammingLanguageContextFilterService languageFilter;

    public HyperionProgrammingExerciseContextRendererService(RepositoryService repositoryService, HyperionProgrammingLanguageContextFilterService languageFilter) {
        this.repositoryService = repositoryService;
        this.languageFilter = languageFilter;
    }

    /**
     * Renders the exercise's problem statement, template repository, and solution repository as one text.
     *
     * @param exercise the exercise to render, may be null
     * @return the snapshot, or an empty string for a null exercise
     */
    public String renderContext(ProgrammingExercise exercise) {
        if (exercise == null) {
            return "";
        }
        String problemStatement = Objects.requireNonNullElse(exercise.getProblemStatement(), "");
        SECRET_MATERIAL_POLICY.requireSafe("problem_statement.md", problemStatement.getBytes(StandardCharsets.UTF_8), HyperionSecretMaterialPolicy.Origin.CLASSIC_CONTEXT);
        ProgrammingLanguage language = exercise.getProgrammingLanguage();
        Map<String, String> templateRepoFiles = fetchRepoContents(exercise.getTemplateParticipation() == null ? null : exercise.getTemplateParticipation().getVcsRepositoryUri(),
                "template", exercise.getId());
        Map<String, String> solutionRepoFiles = fetchRepoContents(exercise.getSolutionParticipation() == null ? null : exercise.getSolutionParticipation().getVcsRepositoryUri(),
                "solution", exercise.getId());

        templateRepoFiles = languageFilter.filter(templateRepoFiles, language);
        solutionRepoFiles = languageFilter.filter(solutionRepoFiles, language);

        List<String> parts = new ArrayList<>(3);
        parts.add(renderProblemStatement(problemStatement));
        parts.add(renderRepository(templateRepoFiles, "Template Repository"));
        parts.add(renderRepository(solutionRepoFiles, "Solution Repository"));
        return String.join("\n\n", parts);
    }

    private Map<String, String> fetchRepoContents(@Nullable LocalVCRepositoryUri localVCRepositoryUri, String label, long exerciseId) {
        if (localVCRepositoryUri == null) {
            return Map.of();
        }
        try {
            return repositoryService.getFilesContentFromBareRepositoryForLastCommit(localVCRepositoryUri);
        }
        catch (IOException ex) {
            log.warn("Could not fetch {} repository contents for exercise {} ({})", label, exerciseId, ex.getClass().getSimpleName());
            return Map.of();
        }
    }

    /** The statement is rendered as if it were a single file, so that a prompt can address it by path and line just like any other part of the exercise. */
    private static String renderProblemStatement(String problemStatement) {
        return renderSection("Problem Statement", renderFileString(null, "problem_statement.md", problemStatement));
    }

    private static String renderRepository(Map<String, String> files, String repositoryName) {
        String root = repositoryName.replace(" ", "_").toLowerCase();
        List<String> fileParts = new ArrayList<>(files.size());
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(file -> fileParts.add(renderFileString(root, file.getKey(), file.getValue())));
        return renderSection(repositoryName, renderFileStructure(root, files.keySet()) + "\n\n" + String.join("\n\n", fileParts));
    }

    private static String renderSection(String sectionName, String body) {
        return "\n===== " + sectionName + " =====\n" + body;
    }

    private static final class DirNode {

        final TreeMap<String, DirNode> dirs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        final TreeSet<String> files = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    }

    private static String renderFileStructure(String root, Iterable<String> paths) {
        DirNode rootNode = new DirNode();
        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            String[] segments = Arrays.stream(path.split("/")).filter(s -> !s.isBlank()).toArray(String[]::new);
            if (Arrays.stream(segments).anyMatch(seg -> seg.startsWith("."))) {
                continue;
            }
            DirNode cursor = rootNode;
            for (int i = 0; i < segments.length; i++) {
                String seg = segments[i];
                boolean last = i == segments.length - 1;
                if (last) {
                    cursor.files.add(seg);
                }
                else {
                    cursor = cursor.dirs.computeIfAbsent(seg, k -> new DirNode());
                }
            }
        }
        List<String> lines = new ArrayList<>();
        if (root != null && !root.isBlank()) {
            lines.add(root);
        }
        collectTree(lines, rootNode, "");
        return String.join("\n", lines);
    }

    private static void collectTree(List<String> lines, DirNode node, String prefix) {
        List<String> dirNames = new ArrayList<>(node.dirs.keySet());
        List<String> fileNames = new ArrayList<>(node.files);
        int total = dirNames.size() + fileNames.size();
        int index = 0;
        for (String dir : dirNames) {
            boolean last = ++index == total;
            lines.add(prefix + (last ? "└── " : "├── ") + dir);
            collectTree(lines, node.dirs.get(dir), prefix + (last ? "    " : "│   "));
        }
        for (String file : fileNames) {
            boolean last = ++index == total;
            lines.add(prefix + (last ? "└── " : "├── ") + file);
        }
    }

    private static final int HR_WIDTH = 80;

    private static String renderFileString(String root, String path, String content) {
        String horizontalRule = "-".repeat(HR_WIDTH);
        String fullPath = root != null && !root.isBlank() ? root + "/" + path : path;
        List<String> lines = Arrays.asList(Objects.requireNonNullElse(content, "").split("\n", -1));
        int lineNumberWidth = Integer.toString(lines.size()).length();
        List<String> renderedLines = new ArrayList<>(lines.size() + 1);
        renderedLines.add(horizontalRule + "\n" + fullPath + ":\n" + horizontalRule);
        IntStream.range(0, lines.size()).forEach(i -> renderedLines.add(("%" + lineNumberWidth + "d").formatted(i + 1) + " | " + lines.get(i)));
        return String.join("\n", renderedLines);
    }

    /**
     * Walks a checked-out repository and draws its current layout as a tree.
     *
     * @param repository the checked-out repository to walk
     * @return the tree, or a sentence saying it could not be determined, since the caller puts this straight into a prompt
     */
    public String getRepositoryStructure(Repository repository) {
        try {
            File repositoryRoot = repository.getLocalPath().toFile();
            if (!repositoryRoot.exists() || !repositoryRoot.isDirectory()) {
                log.warn("Repository path does not exist or is not a directory: {}", safePath(repositoryRoot.toPath()));
                return "Repository structure could not be determined.";
            }

            HyperionSecretMaterialPolicy.Assessment rootAssessment = SECRET_MATERIAL_POLICY.assess(repositoryRoot.getName(), new byte[0],
                    HyperionSecretMaterialPolicy.Origin.CLASSIC_CONTEXT);
            if (!rootAssessment.isSafe()) {
                log.debug("Skipping Hyperion repository structure [{}]: {}", rootAssessment.category().orElseThrow(), rootAssessment.safePath());
                return "Repository structure could not be determined.";
            }
            StringBuilder structure = new StringBuilder();
            structure.append(repositoryRoot.getName()).append("/").append("\n");
            generateTreeStructure(repositoryRoot, repositoryRoot, structure, "");

            return structure.toString();

        }
        catch (Exception e) {
            log.error("Failed to generate repository structure for repository {} ({})", safePath(repository.getLocalPath()), e.getClass().getSimpleName());
            return "Repository structure could not be determined due to an error.";
        }
    }

    /**
     * Renders the build files of a repository, which are what decides whether generated code compiles and its tests run.
     *
     * @param repository the checked-out repository to read, may be null
     * @return the rendered build files, or a sentence saying there are none
     */
    public String getBuildEnvironmentContext(Repository repository) {
        if (repository == null || repository.getLocalPath() == null) {
            return "No build environment files found.";
        }

        Path repositoryPath = repository.getLocalPath();
        if (!Files.isDirectory(repositoryPath)) {
            return "No build environment files found.";
        }

        try (var walk = Files.walk(repositoryPath)) {
            Map<String, String> buildFiles = new TreeMap<>();
            walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).filter(path -> isRelevantBuildEnvironmentFile(repositoryPath, path))
                    .sorted(Comparator.comparing(path -> repositoryPath.relativize(path).toString(), String.CASE_INSENSITIVE_ORDER)).forEach(path -> {
                        String relativePath = repositoryPath.relativize(path).toString().replace('\\', '/');
                        String content = readBuildEnvironmentFile(path);
                        HyperionSecretMaterialPolicy.Assessment assessment = SECRET_MATERIAL_POLICY.assess(relativePath, content.getBytes(StandardCharsets.UTF_8),
                                HyperionSecretMaterialPolicy.Origin.CLASSIC_CONTEXT);
                        if (assessment.isSafe()) {
                            buildFiles.put(relativePath, content);
                        }
                        else {
                            log.debug("Skipping Hyperion build context file [{}]: {}", assessment.category().orElseThrow(), assessment.safePath());
                        }
                    });

            if (buildFiles.isEmpty()) {
                return "No build environment files found.";
            }
            return renderRepository(buildFiles, "Build Environment Files");
        }
        catch (IOException | UncheckedIOException e) {
            log.warn("Failed to render build environment context for repository {}", safePath(repositoryPath));
            return "Build environment files could not be determined.";
        }
    }

    private void generateTreeStructure(File repositoryRoot, File directory, StringBuilder structure, String prefix) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        File[] filteredFiles = Arrays.stream(files).filter(file -> !EXCLUDED_DIRECTORIES.contains(file.getName()) && !EXCLUDED_FILES.contains(file.getName())).filter(file -> {
            String relativePath = repositoryRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/');
            HyperionSecretMaterialPolicy.Assessment assessment = SECRET_MATERIAL_POLICY.assess(relativePath, new byte[0], HyperionSecretMaterialPolicy.Origin.CLASSIC_CONTEXT);
            if (!assessment.isSafe()) {
                log.debug("Skipping Hyperion repository structure entry [{}]: {}", assessment.category().orElseThrow(), assessment.safePath());
            }
            return assessment.isSafe();
        }).sorted((a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) {
                return -1;
            }
            else if (!a.isDirectory() && b.isDirectory()) {
                return 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        }).toArray(File[]::new);

        for (int i = 0; i < filteredFiles.length; i++) {
            File file = filteredFiles[i];
            boolean isLastFile = (i == filteredFiles.length - 1);

            structure.append(prefix);
            structure.append(isLastFile ? "└── " : "├── ");
            structure.append(file.getName());

            if (file.isDirectory()) {
                structure.append("/");
            }
            structure.append("\n");

            if (file.isDirectory()) {
                String newPrefix = prefix + (isLastFile ? "    " : "│   ");
                generateTreeStructure(repositoryRoot, file, structure, newPrefix);
            }
        }
    }

    private boolean isRelevantBuildEnvironmentFile(Path repositoryRoot, Path path) {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }

        Path relativePath = repositoryRoot.relativize(path);
        if (relativePath.getNameCount() == 0) {
            return false;
        }

        for (int i = 0; i < relativePath.getNameCount() - 1; i++) {
            String segment = relativePath.getName(i).toString();
            if (segment.startsWith(".") || EXCLUDED_DIRECTORIES.contains(segment)) {
                return false;
            }
        }

        return BUILD_ENVIRONMENT_FILES.contains(relativePath.getFileName().toString());
    }

    private String readBuildEnvironmentFile(Path path) {
        int maxCharsToRead = MAX_BUILD_ENVIRONMENT_FILE_CONTENT_LENGTH + 1;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            char[] buffer = new char[maxCharsToRead];
            int offset = 0;
            while (offset < buffer.length) {
                int read = reader.read(buffer, offset, buffer.length - offset);
                if (read == -1) {
                    break;
                }
                offset += read;
            }
            String content = new String(buffer, 0, offset);
            if (content.length() <= MAX_BUILD_ENVIRONMENT_FILE_CONTENT_LENGTH) {
                return content;
            }
            return content.substring(0, MAX_BUILD_ENVIRONMENT_FILE_CONTENT_LENGTH) + "\n... [truncated]";
        }
        catch (IOException e) {
            log.warn("Failed to read build environment file {} ({})", safePath(path), e.getClass().getSimpleName());
            return "[Failed to read file]";
        }
    }

    /**
     * Concatenates the Java sources under {@code src/} of the exercise's solution repository, each announced by its path.
     *
     * @param exercise   the exercise whose solution to read
     * @param gitService checks the solution repository out
     * @return the solution sources, or a sentence pointing at the problem statement when there are none to read
     * @throws NetworkingException if the solution repository cannot be reached at all
     */
    public String getExistingSolutionCode(ProgrammingExercise exercise, GitService gitService) throws NetworkingException {
        try {
            var solutionRepositoryUri = exercise.getVcsSolutionRepositoryUri();
            if (solutionRepositoryUri == null) {
                log.warn("No solution repository URI found for exercise {}, using problem statement only", exercise.getId());
                return "No solution code available. Please refer to the problem statement.";
            }

            Repository solutionRepository = gitService.getOrCheckoutRepository(solutionRepositoryUri, true, "main", false);
            if (solutionRepository == null) {
                log.warn("Failed to access solution repository for exercise {}", exercise.getId());
                return "Solution repository not accessible. Please refer to the problem statement.";
            }

            Path repositoryPath = solutionRepository.getLocalPath();
            StringBuilder solutionCode = new StringBuilder();

            try (var paths = Files.walk(repositoryPath)) {
                paths.filter(path -> path.toString().endsWith(".java")).filter(path -> path.toString().contains("src/")).filter(Files::isRegularFile).forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        String relativePath = repositoryPath.relativize(path).toString().replace('\\', '/');
                        HyperionSecretMaterialPolicy.Assessment assessment = SECRET_MATERIAL_POLICY.assess(relativePath, content.getBytes(StandardCharsets.UTF_8),
                                HyperionSecretMaterialPolicy.Origin.CLASSIC_CONTEXT);
                        if (assessment.isSafe()) {
                            solutionCode.append("// File: ").append(relativePath).append("\n");
                            solutionCode.append(content).append("\n\n");
                        }
                    }
                    catch (IOException e) {
                        log.warn("Failed to read solution context file {}", safePath(path));
                    }
                });
            }
            catch (IOException e) {
                log.error("Failed to scan solution repository for exercise {} ({})", exercise.getId(), e.getClass().getSimpleName());
                return "Failed to read solution code. Please refer to the problem statement.";
            }

            return solutionCode.length() > 0 ? solutionCode.toString() : "No solution code found. Please refer to the problem statement.";

        }
        catch (Exception e) {
            log.error("Error accessing solution repository for exercise {} ({})", exercise.getId(), e.getClass().getSimpleName());
            throw new NetworkingException("Failed to access solution repository", e);
        }
    }

    /**
     * Reads the exercise's test sources, so that generated code can match the exact API and behaviour the tests demand.
     *
     * @param exercise   the exercise whose tests to read
     * @param gitService checks the test repository out
     * @return the test sources, or a marker saying there are none — never throws, so generation can still proceed from the problem statement alone
     */
    public String getExistingTestCode(ProgrammingExercise exercise, GitService gitService) {
        String noTests = "No tests available yet.";
        try {
            var testRepositoryUri = exercise.getVcsTestRepositoryUri();
            if (testRepositoryUri == null) {
                return noTests;
            }
            Repository testRepository = gitService.getOrCheckoutRepository(testRepositoryUri, true, "main", false);
            if (testRepository == null) {
                return noTests;
            }
            Path repositoryPath = testRepository.getLocalPath();
            StringBuilder testCode = new StringBuilder();
            try (var paths = Files.walk(repositoryPath)) {
                // The structural spec (test.json) comes first, because it is the definitive contract and the caller caps the length of what it passes on.
                // Symbolic links are skipped: following one would let a link committed to the repository pull server-local content into the prompt.
                paths.filter(path -> path.toString().endsWith(".java") || path.getFileName().toString().equals("test.json"))
                        .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .sorted(Comparator.comparing((Path path) -> isStructuralSpec(path) ? 0 : 1).thenComparing(Path::toString)).forEach(path -> {
                            try {
                                String relativePath = repositoryPath.relativize(path).toString().replace('\\', '/');
                                String content = Files.readString(path);
                                HyperionSecretMaterialPolicy.Assessment assessment = SECRET_MATERIAL_POLICY.assess(relativePath, content.getBytes(StandardCharsets.UTF_8),
                                        HyperionSecretMaterialPolicy.Origin.CLASSIC_CONTEXT);
                                if (assessment.isSafe()) {
                                    testCode.append("// File: ").append(relativePath).append("\n").append(content).append("\n\n");
                                }
                            }
                            catch (IOException e) {
                                log.warn("Failed to read test context file {}", safePath(path));
                            }
                        });
            }
            return testCode.length() > 0 ? testCode.toString() : noTests;
        }
        catch (Exception e) {
            log.warn("Could not read test repository for exercise {} ({}). Generating from the problem statement only.", exercise.getId(), e.getClass().getSimpleName());
            return noTests;
        }
    }

    private static boolean isStructuralSpec(Path path) {
        return "test.json".equals(path.getFileName().toString());
    }

    private static String safePath(Path path) {
        return SECRET_MATERIAL_POLICY.assess(path == null ? null : path.toString(), new byte[0], HyperionSecretMaterialPolicy.Origin.CLASSIC_CONTEXT).safePath();
    }
}
