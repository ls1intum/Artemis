package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceFile;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.GenerationRequestService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/** Captures exact Git trees on core; the worker receives bytes and expected heads never leave core. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationSeedService {

    private final GitService gitService;

    private final TempFileUtilService temporaryFiles;

    private final GenerationRequestService requests;

    private final String branch;

    public GenerationSeedService(GitService gitService, TempFileUtilService temporaryFiles, GenerationRequestService requests,
            @Value("${artemis.version-control.default-branch:main}") String branch) {
        this.gitService = gitService;
        this.temporaryFiles = temporaryFiles;
        this.requests = requests;
        this.branch = branch;
    }

    /**
     * @param exercise the authorized, eagerly loaded draft
     * @return bounded immutable files and the core-only compare-and-set heads
     */
    public Seed capture(ProgrammingExercise exercise) {
        List<WorkspaceFile> files = new ArrayList<>();
        Map<RepositoryType, String> heads = new EnumMap<>(RepositoryType.class);
        for (RepositoryType role : List.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS)) {
            captureRepository(exercise, role, files, heads);
        }
        String statement = requests.isAuthoritativeProblemStatement(exercise) ? exercise.getProblemStatement() : "";
        files.add(new WorkspaceFile("problem-statement.md", statement.getBytes(StandardCharsets.UTF_8), false));
        return new Seed(new WorkspaceSnapshot(files), Map.copyOf(heads));
    }

    private void captureRepository(ProgrammingExercise exercise, RepositoryType role, List<WorkspaceFile> files, Map<RepositoryType, String> heads) {
        Path temporary = null;
        Repository repository = null;
        try {
            temporary = temporaryFiles.createTempDirectory("hyperion-seed-");
            var uri = exercise.getRepositoryURI(role);
            if (uri == null) {
                throw new IllegalStateException("Missing generation repository: " + role);
            }
            repository = gitService.getOrCheckoutRepository(uri, uri, temporary.resolve("repository"), true, branch, false);
            try (RevWalk commits = new RevWalk(repository); TreeWalk tree = new TreeWalk(repository)) {
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
        catch (Exception e) {
            throw new IllegalStateException("Could not capture the " + role + " repository for generation", e);
        }
        finally {
            if (repository != null) {
                repository.closeBeforeDelete();
            }
            try {
                if (temporary != null) {
                    FileUtils.deleteDirectory(temporary.toFile());
                }
            }
            catch (IOException e) {
                throw new IllegalStateException("Could not remove the temporary generation checkout", e);
            }
        }
    }

    /** File transport and optimistic concurrency evidence are deliberately kept separate. */
    public record Seed(WorkspaceSnapshot snapshot, Map<RepositoryType, String> heads) {
    }
}
