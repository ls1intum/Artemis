package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.protocol.GradingContext;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceFile;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperion.runtime.verification.AresTestHarness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.GenerationRequestService;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;

/** Captures exact Git trees on core; the worker receives bytes and expected heads never leave core. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationSeedService {

    private final ProgrammingExerciseBuildConfigRepository buildConfigRepository;

    private final GitService gitService;

    private final BareGitRepositoryService bareRepositories;

    private final TempFileUtilService temporaryFiles;

    private final GenerationRequestService requests;

    private final String branch;

    private final ProgrammingExerciseTestCaseRepository testCases;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    public GenerationSeedService(ProgrammingExerciseBuildConfigRepository buildConfigRepository, GitService gitService, TempFileUtilService temporaryFiles,
            GenerationRequestService requests, ProgrammingExerciseTestCaseRepository testCases, ProgrammingExerciseTaskService programmingExerciseTaskService,
            BareGitRepositoryService bareRepositories, @Value("${artemis.version-control.default-branch:main}") String branch) {
        this.buildConfigRepository = buildConfigRepository;
        this.gitService = gitService;
        this.bareRepositories = bareRepositories;
        this.temporaryFiles = temporaryFiles;
        this.requests = requests;
        this.testCases = testCases;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.branch = branch;
    }

    /**
     * @param exercise the authorized, eagerly loaded draft
     * @return bounded immutable files and the core-only compare-and-set heads
     */
    public Seed capture(ProgrammingExercise exercise) {
        String configuredBranch = buildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(exercise.getId()).getBranch();
        String repositoryBranch = configuredBranch == null || configuredBranch.isBlank() ? branch : configuredBranch;
        List<WorkspaceFile> files = new ArrayList<>();
        Map<RepositoryType, String> heads = new EnumMap<>(RepositoryType.class);
        for (RepositoryType role : List.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS)) {
            captureRepository(exercise, role, files, heads, repositoryBranch);
        }
        ProgrammingExercise readable = withTestIdsRenderedAsNames(exercise);
        String statement = requests.isAuthoritativeProblemStatement(readable) ? readable.getProblemStatement() : "";
        files.add(new WorkspaceFile("problem-statement.md", statement.getBytes(StandardCharsets.UTF_8), false));
        var baseline = testCases.findByExerciseId(exercise.getId()).stream().filter(test -> Boolean.TRUE.equals(test.isActive()) && !test.isInvisible())
                .filter(test -> test.getType() != ProgrammingExerciseTestCaseType.STRUCTURAL)
                .filter(test -> test.getBonusPoints() > 0 || (test.getWeight() != null && test.getWeight() > 0 && test.getBonusMultiplier() > 0)).map(test -> test.getTestName())
                .filter(name -> name != null && !name.isBlank()).collect(Collectors.toSet());
        return new Seed(new WorkspaceSnapshot(files), Map.copyOf(heads), new GradingContext(exercise.getDueDate() != null, baseline));
    }

    /**
     * Reads only three trusted blobs at the configured tests branch, without cloning or checking out any repository.
     *
     * @param exercise authorized source
     * @return bounded committed harness files; missing paths remain absent for fail-closed validation
     */
    public Map<String, String> readTestHarness(ProgrammingExercise exercise) {
        String configuredBranch = buildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(exercise.getId()).getBranch();
        String repositoryBranch = configuredBranch == null || configuredBranch.isBlank() ? branch : configuredBranch;
        try {
            var uri = exercise.getRepositoryURI(RepositoryType.TESTS);
            if (uri == null) {
                throw new IllegalStateException("Missing generation tests repository");
            }
            try (Repository repository = bareRepositories.getExistingBareRepository(uri, repositoryBranch); RevWalk commits = new RevWalk(repository)) {
                var ref = repository.exactRef(Constants.R_HEADS + repositoryBranch);
                if (ref == null) {
                    throw new IllegalStateException("Missing generation tests branch");
                }
                var commit = commits.parseCommit(ref.getObjectId());
                Map<String, String> files = new LinkedHashMap<>();
                for (String path : AresTestHarness.PATHS) {
                    try (TreeWalk tree = TreeWalk.forPath(repository, path, commit.getTree())) {
                        if (tree == null) {
                            continue;
                        }
                        if (!tree.getFileMode(0).equals(FileMode.REGULAR_FILE) && !tree.getFileMode(0).equals(FileMode.EXECUTABLE_FILE)) {
                            throw new IllegalArgumentException("Generation harness files must be regular files");
                        }
                        var blob = repository.open(tree.getObjectId(0), Constants.OBJ_BLOB);
                        if (blob.getSize() > WorkspaceFile.MAX_FILE_BYTES) {
                            throw new IllegalArgumentException("Generation harness file exceeds the byte limit");
                        }
                        files.put(path, new String(blob.getBytes(WorkspaceFile.MAX_FILE_BYTES), StandardCharsets.UTF_8));
                    }
                }
                return Map.copyOf(files);
            }
        }
        catch (Exception e) {
            throw new IllegalStateException("Could not read the tests harness for generation", e);
        }
    }

    /**
     * A saved problem statement binds tasks and diagrams to {@code <testid>} references, which the worker cannot resolve: its verifier reports each as an unbound task and the
     * agent spends turns repairing them. The seed therefore carries test names, as an editor would show them. The rendering happens on a detached copy because the loaded
     * entity's statement is the compare-and-set expectation for the later save and must keep its ids. The copy carries language and project type so the default-readme
     * comparison in {@link GenerationRequestService#isAuthoritativeProblemStatement(ProgrammingExercise)} sees the same shape (names) the shipped template uses.
     */
    private ProgrammingExercise withTestIdsRenderedAsNames(ProgrammingExercise exercise) {
        ProgrammingExercise copy = new ProgrammingExercise();
        copy.setId(exercise.getId());
        copy.setProblemStatement(exercise.getProblemStatement());
        copy.setProgrammingLanguage(exercise.getProgrammingLanguage());
        copy.setProjectType(exercise.getProjectType());
        programmingExerciseTaskService.replaceTestIdsWithNames(copy);
        return copy;
    }

    private void captureRepository(ProgrammingExercise exercise, RepositoryType role, List<WorkspaceFile> files, Map<RepositoryType, String> heads, String repositoryBranch) {
        try {
            Path temporary = temporaryFiles.createTempDirectory("hyperion-seed-");
            try (AutoCloseable temporaryCheckout = () -> FileUtils.deleteDirectory(temporary.toFile())) {
                var uri = exercise.getRepositoryURI(role);
                if (uri == null) {
                    throw new IllegalStateException("Missing generation repository: " + role);
                }
                Repository repository = gitService.getOrCheckoutRepositoryOnBranch(uri, temporary.resolve("repository"), repositoryBranch);
                try (AutoCloseable repositoryClose = repository::closeBeforeDelete; RevWalk commits = new RevWalk(repository); TreeWalk tree = new TreeWalk(repository)) {
                    var commit = commits.parseCommit(repository.resolve(Constants.HEAD));
                    heads.put(role, commit.name());
                    tree.addTree(commit.getTree());
                    tree.setRecursive(true);
                    long total = files.stream().mapToLong(WorkspaceFile::size).sum();
                    while (tree.next()) {
                        FileMode mode = tree.getFileMode(0);
                        if (!mode.equals(FileMode.REGULAR_FILE) && !mode.equals(FileMode.EXECUTABLE_FILE)) {
                            throw new IllegalArgumentException("Generation does not accept repository links or submodules");
                        }
                        var blob = repository.open(tree.getObjectId(0), Constants.OBJ_BLOB);
                        total = Math.addExact(total, blob.getSize());
                        if (total > WorkspaceSnapshot.MAX_BYTES || files.size() >= WorkspaceSnapshot.MAX_FILES) {
                            throw new IllegalArgumentException("Generation repository seed exceeds its bounds");
                        }
                        files.add(new WorkspaceFile(role.name().toLowerCase(Locale.ROOT) + "/" + tree.getPathString(), blob.getBytes(WorkspaceFile.MAX_FILE_BYTES),
                                mode.equals(FileMode.EXECUTABLE_FILE)));
                    }
                }
            }
        }
        catch (Exception e) {
            throw new IllegalStateException("Could not capture the " + role + " repository for generation", e);
        }
    }

    /** File transport and optimistic concurrency evidence are deliberately kept separate. */
    public record Seed(WorkspaceSnapshot snapshot, Map<RepositoryType, String> heads, GradingContext gradingContext) {
    }
}
