package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Clears the template/solution source files of a programming exercise so the repositories are an empty, buildable starting point for AI exercise generation (Hyperion): the agent
 * authors the sources from scratch. The build scaffolding (manifests, dotfiles, test harness) is kept in place so the cleared repository is still a valid, buildable clone target.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseRepositorySourceCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseRepositorySourceCleanupService.class);

    /**
     * Build-manifest / config filenames that must be kept even when their extension matches a language source extension (e.g. {@code Package.swift} is a Swift manifest, not a
     * deletable {@code .swift} source).
     */
    private static final Set<String> AI_GENERATION_KEEP_FILES = Set.of("Package.swift", "build.sbt");

    private final GitService gitService;

    public ProgrammingExerciseRepositorySourceCleanupService(GitService gitService) {
        this.gitService = gitService;
    }

    /**
     * Clears the template and solution repositories for AI generation (the agent authors these from scratch). The TESTS repository is intentionally <strong>kept intact</strong> as
     * the coherent canonical test scaffold the agent adapts: clearing its example test sources would break harnesses that import or otherwise depend on them — C's {@code Tests.py}
     * imports its concrete test modules, OCaml's {@code test/} holds the dune build target, and Rust's proc-macro/structural reflection, Swift's {@code XCTestManifests}, and
     * Haskell's {@code Interface.hs} are source-extension infrastructure that cannot be regenerated. Keeping the test repo also gives the agent a correct, working reference of the
     * language's test-framework conventions to write its own tests against; the agent replaces the example test cases with its exercise's tests as part of generation.
     *
     * @param programmingLanguage the exercise's programming language (selects the source extensions to clear)
     * @param templateRepository  the template repository to clear
     * @param solutionRepository  the solution repository to clear
     * @param exerciseCreator     the user performing the cleanup (used as Git commit author)
     */
    public void clearRepositoriesForAiGeneration(final ProgrammingLanguage programmingLanguage, final Repository templateRepository, final Repository solutionRepository,
            final User exerciseCreator) {
        clearRepositorySourcesSafely(programmingLanguage, templateRepository, RepositoryType.TEMPLATE, exerciseCreator);
        clearRepositorySourcesSafely(programmingLanguage, solutionRepository, RepositoryType.SOLUTION, exerciseCreator);
    }

    private void clearRepositorySourcesSafely(final ProgrammingLanguage programmingLanguage, final Repository repository, final RepositoryType repositoryType,
            final User exerciseCreator) {
        try {
            clearRepositorySources(programmingLanguage, repository, repositoryType, exerciseCreator);
        }
        catch (IOException | GitAPIException ex) {
            log.warn("Failed to clear {} repository sources for AI generation in {}. Continuing without source cleanup.", repositoryType.name().toLowerCase(Locale.ROOT),
                    repository.getRemoteRepositoryUri(), ex);
        }
    }

    /**
     * Clears the repository sources for AI generation while keeping the build scaffolding (manifests, dotfiles, test harness) in place, so the cleared repository is still a valid,
     * buildable starting point. Works for every language: conventional source directories ({@code src}, {@code lib}, {@code Sources}, …) are emptied, and any loose source files of
     * the exercise's language anywhere else in the tree — including languages that keep their sources at the repository root (C, Go, Haskell, OCaml, Bash, …) and extra directories
     * (C++ {@code include/}, Dart {@code bin/}) — are deleted by extension, with the build manifests allowlisted.
     *
     * @param programmingLanguage the exercise's programming language (selects the source extensions to clear)
     * @param repository          the repository to clean
     * @param repositoryType      the repository type for logging and commit message
     * @param exerciseCreator     the user performing the cleanup
     * @throws IOException     If file cleanup in the repository fails.
     * @throws GitAPIException If committing, or pushing to the repo throws an exception.
     */
    void clearRepositorySources(final ProgrammingLanguage programmingLanguage, final Repository repository, final RepositoryType repositoryType, final User exerciseCreator)
            throws IOException, GitAPIException {
        final String repositoryLabel = repositoryType.name().toLowerCase(Locale.ROOT);
        final Path repositoryRoot = repository.getLocalPath();
        // Delete the language's source files anywhere in the repo (root-source languages, src/, include/, Sources/, …) by extension, keeping build manifests — including manifests
        // that live INSIDE a source directory, such as OCaml's src/dune. (A blunt "empty the whole src/ directory" would wipe src/dune and break the build.)
        boolean changed = deleteLooseLanguageSources(repositoryRoot, programmingLanguage);
        // Preserve a conventional source directory that the clearing emptied, so the expected layout survives (git drops empty directories). A directory that still holds a
        // manifest
        // (e.g. OCaml's src/ with its dune file) is left as-is.
        for (Path sourceDirectory : conventionalSourceDirectories(repositoryRoot, repositoryType)) {
            if (isEmptyDirectory(sourceDirectory)) {
                ensureGitKeep(sourceDirectory);
                changed = true;
            }
        }
        if (!changed) {
            log.info("Nothing to clear in {} repository {} for AI generation (no source files of the language found).", repositoryLabel, repository.getRemoteRepositoryUri());
            return;
        }
        // Keep the repository non-empty (a valid clone target) when clearing emptied it (e.g. C/FACT, whose only file is the single root source).
        ensureRepositoryNotEmpty(repositoryRoot);
        commitAndPushRepository(repository, "Cleared " + repositoryLabel + " sources for AI generation", true, exerciseCreator);
    }

    private void commitAndPushRepository(final Repository repository, final String message, final boolean emptyCommit, final User user) throws GitAPIException {
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, message, emptyCommit, user);
    }

    /** Whether the directory exists and contains no regular files anywhere beneath it (it may contain empty sub-directories). */
    private static boolean isEmptyDirectory(final Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.noneMatch(Files::isRegularFile);
        }
    }

    private static void ensureGitKeep(final Path directory) throws IOException {
        Path keepFile = directory.resolve(".gitkeep");
        if (!Files.exists(keepFile)) {
            Files.createFile(keepFile);
        }
    }

    /**
     * The conventional source directories for the template/solution repos whose emptied state should be preserved with a {@code .gitkeep} so the expected layout survives (git
     * drops
     * empty directories). Covers the source-dir conventions across languages ({@code src}, {@code lib}, {@code Sources}, R's {@code R}, C++'s {@code include}, Dart's {@code bin},
     * …).
     * The TESTS repository is never cleared (see {@link #clearRepositoriesForAiGeneration}), so it has none.
     */
    private static List<Path> conventionalSourceDirectories(final Path repositoryRoot, final RepositoryType repositoryType) {
        if (repositoryType == RepositoryType.TESTS) {
            return List.of();
        }
        return Stream.of("src", "sources", "lib", "Sources", "R", "include", "bin").map(repositoryRoot::resolve).filter(Files::isDirectory).toList();
    }

    /**
     * Deletes loose source files of the given language anywhere under the repository (outside {@code .git}), keeping build manifests (allowlisted by name and by virtue of having a
     * non-source extension: go.mod, Makefile, Cargo.toml, package.json, pubspec.yaml, *.csproj, CMakeLists.txt, DESCRIPTION, dune, dotfiles, …). Returns whether anything was
     * deleted.
     */
    private static boolean deleteLooseLanguageSources(final Path repositoryRoot, final ProgrammingLanguage programmingLanguage) throws IOException {
        final Set<String> sourceExtensions = sourceExtensionsFor(programmingLanguage);
        if (sourceExtensions.isEmpty() || !Files.isDirectory(repositoryRoot)) {
            return false;
        }
        final List<Path> toDelete;
        try (Stream<Path> files = Files.walk(repositoryRoot)) {
            toDelete = files.filter(Files::isRegularFile).filter(path -> !repositoryRoot.relativize(path).startsWith(".git"))
                    .filter(path -> !AI_GENERATION_KEEP_FILES.contains(path.getFileName().toString())).filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return sourceExtensions.stream().anyMatch(name::endsWith);
                    }).toList();
        }
        for (Path path : toDelete) {
            Files.delete(path);
        }
        return !toDelete.isEmpty();
    }

    /** Maps a language to the file extensions (lower-cased) that identify its student-editable source files for AI-generation clearing. */
    private static Set<String> sourceExtensionsFor(final ProgrammingLanguage programmingLanguage) {
        if (programmingLanguage == null) {
            return Set.of();
        }
        return switch (programmingLanguage) {
            case JAVA -> Set.of(".java");
            case KOTLIN -> Set.of(".kt");
            case PYTHON -> Set.of(".py");
            case C -> Set.of(".c", ".h");
            case C_PLUS_PLUS -> Set.of(".cpp", ".cc", ".cxx", ".hpp", ".hh", ".h");
            case C_SHARP -> Set.of(".cs");
            case GO -> Set.of(".go");
            case RUST -> Set.of(".rs");
            case SWIFT -> Set.of(".swift");
            case HASKELL -> Set.of(".hs");
            case OCAML -> Set.of(".ml", ".mli");
            case JAVASCRIPT -> Set.of(".js");
            case TYPESCRIPT -> Set.of(".ts");
            case RUBY -> Set.of(".rb");
            case R -> Set.of(".r");
            case DART -> Set.of(".dart");
            case VHDL -> Set.of(".vhd", ".vhdl");
            case ASSEMBLER -> Set.of(".asm", ".s");
            case BASH -> Set.of(".bash", ".sh");
            case MATLAB -> Set.of(".m");
            default -> Set.of();
        };
    }

    /** Ensures the repository has at least one tracked file (a root {@code .gitkeep}) so the AI-generation clearing commit is non-empty and the repo stays a valid clone target. */
    private static void ensureRepositoryNotEmpty(final Path repositoryRoot) throws IOException {
        final boolean hasTrackedFile;
        try (Stream<Path> files = Files.walk(repositoryRoot)) {
            hasTrackedFile = files.filter(Files::isRegularFile).anyMatch(path -> !repositoryRoot.relativize(path).startsWith(".git"));
        }
        if (!hasTrackedFile) {
            Files.createFile(repositoryRoot.resolve(".gitkeep"));
        }
    }
}
