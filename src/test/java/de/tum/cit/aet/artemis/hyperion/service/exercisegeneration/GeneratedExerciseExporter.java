package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Test-only helper that writes the full output of an agentic generation run to a clean directory tree on disk, so a generated exercise can be opened, read, and diffed against the
 * canonical Artemis exercise templates in {@code src/main/resources/templates/} (the "sample exercises" the agent starts from). Each run's artifacts are far more useful as real
 * files than as interleaved log lines.
 * <p>
 * Layout under {@code <base>/<mode>/}: {@code problem-statement.md}, {@code transcript.txt}, {@code verification.txt}, {@code summary.json} (machine-readable: gates + per-repo
 * file
 * counts), and one sub-directory per repository ({@code solution/}, {@code template/}, {@code tests/}) holding the produced files at their workspace-relative paths.
 * <p>
 * The base directory defaults to {@code build/hyperion-e2e} (git-ignored) and can be overridden with the {@code HYPERION_E2E_EXPORT_DIR} environment variable.
 */
final class GeneratedExerciseExporter {

    private static final Logger log = LoggerFactory.getLogger(GeneratedExerciseExporter.class);

    private static final String DEFAULT_BASE_DIR = "build/hyperion-e2e";

    private static final RepositoryType[] EXPORTED_REPOSITORIES = { RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS };

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private GeneratedExerciseExporter() {
    }

    /**
     * Writes the run's produced problem statement, repositories, verification verdict, and agent transcript under {@code <base>/<mode>/}, replacing any previous export for the
     * same
     * mode. Must be called while the outcome is still open (before {@link GenerationOutcome#close()}), as the files are read lazily from the live sandbox session.
     *
     * @param mode       a short, file-system-safe label for the scenario (e.g. {@code "realistic-bare-topic"})
     * @param outcome    the still-open generation outcome
     * @param transcript the full agent transcript, or {@code null} if not captured
     * @return the directory the artifacts were written to
     */
    static Path export(String mode, GenerationOutcome outcome, @Nullable String transcript) {
        try {
            Path base = baseDirectory().resolve(sanitize(mode));
            deleteRecursively(base);
            Files.createDirectories(base);

            writeString(base.resolve("problem-statement.md"), outcome.producedProblemStatement());
            if (transcript != null) {
                writeString(base.resolve("transcript.txt"), transcript);
            }
            writeString(base.resolve("verification.txt"), renderVerification(outcome));

            Map<String, Integer> repositoryFileCounts = new LinkedHashMap<>();
            for (RepositoryType repositoryType : EXPORTED_REPOSITORIES) {
                Map<String, String> files = outcome.producedFiles(repositoryType);
                Path repositoryDirectory = base.resolve(folderName(repositoryType));
                files.forEach((relativePath, content) -> writeRepositoryFile(repositoryDirectory, relativePath, content));
                repositoryFileCounts.put(folderName(repositoryType), files.size());
            }

            writeString(base.resolve("summary.json"), renderSummary(mode, outcome, repositoryFileCounts));
            log.info("Exported generated exercise '{}' to {} (files: {})", mode, base.toAbsolutePath(), repositoryFileCounts);
            return base;
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to export generated exercise for mode " + mode, e);
        }
    }

    private static Path baseDirectory() {
        return Path.of(System.getenv().getOrDefault("HYPERION_E2E_EXPORT_DIR", DEFAULT_BASE_DIR));
    }

    private static String folderName(RepositoryType repositoryType) {
        return repositoryType.name().toLowerCase();
    }

    /** Writes one produced file under its repository directory, creating parent directories and refusing any path that would escape the export root. */
    private static void writeRepositoryFile(Path repositoryDirectory, String relativePath, String content) {
        if (relativePath.isBlank()) {
            return;
        }
        Path root = repositoryDirectory.normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            log.warn("Skipping export of '{}': it resolves outside the export directory", relativePath);
            return;
        }
        writeString(target, content);
    }

    private static void writeString(Path target, String content) {
        try {
            // FileUtils.writeStringToFile creates the parent directories itself; UTF-8 keeps the bytes identical to the previous Files.writeString default.
            FileUtils.writeStringToFile(target.toFile(), content == null ? "" : content, StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + target, e);
        }
    }

    private static String renderVerification(GenerationOutcome outcome) {
        VerificationResult verification = outcome.verification();
        if (verification == null) {
            return "No verification result (the run did not reach verification).\n";
        }
        return """
                accepted:       %b
                solutionPassed: %b
                templateFailed: %b
                testCount:      %d

                report:
                %s
                """.formatted(verification.accepted(), verification.solutionPassed(), verification.templateFailed(), verification.testCount(), verification.report());
    }

    private static String renderSummary(String mode, GenerationOutcome outcome, Map<String, Integer> repositoryFileCounts) throws IOException {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mode", mode);
        summary.put("accepted", outcome.isAccepted());
        VerificationResult verification = outcome.verification();
        if (verification != null) {
            summary.put("solutionPassed", verification.solutionPassed());
            summary.put("templateFailed", verification.templateFailed());
            summary.put("testCount", verification.testCount());
            summary.put("reasons", verification.reasons());
        }
        summary.put("agentTurns", outcome.loopResult().turns());
        summary.put("repositoryFileCounts", repositoryFileCounts);
        summary.put("problemStatementLength", outcome.producedProblemStatement().length());
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summary) + "\n";
    }

    private static String sanitize(String mode) {
        return mode.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(path)) {
            entries.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.delete(entry);
                }
                catch (IOException e) {
                    throw new UncheckedIOException("Failed to delete " + entry, e);
                }
            });
        }
    }
}
