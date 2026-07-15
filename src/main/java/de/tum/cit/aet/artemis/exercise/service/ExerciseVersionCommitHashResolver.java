package de.tum.cit.aet.artemis.exercise.service;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Resolves the git commit hashes of a programming exercise's repositories for exercise versioning. This keeps the
 * git access (via {@link GitService}) in the service layer so the versioning DTOs stay pure data mappers.
 */
public final class ExerciseVersionCommitHashResolver {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionCommitHashResolver.class);

    private ExerciseVersionCommitHashResolver() {
    }

    /**
     * Resolves the commit hashes for the given exercise, or {@code null} if it is not a programming exercise (only
     * programming exercises have repositories with commit hashes).
     *
     * @param exercise   the exercise to resolve commit hashes for
     * @param gitService the git service used to read the last commit hash of each repository
     * @return the resolved commit hashes, or {@code null} for non-programming exercises
     */
    public static ProgrammingExerciseSnapshotDTO.@Nullable CommitHashesDTO resolveForExercise(Exercise exercise, GitService gitService) {
        if (exercise instanceof ProgrammingExercise programmingExercise) {
            return resolve(programmingExercise, gitService);
        }
        return null;
    }

    /**
     * Resolves the commit hashes of the template, solution, tests and auxiliary repositories of the given programming
     * exercise.
     *
     * @param exercise   the programming exercise to resolve commit hashes for
     * @param gitService the git service used to read the last commit hash of each repository
     * @return the resolved commit hashes
     */
    public static ProgrammingExerciseSnapshotDTO.CommitHashesDTO resolve(ProgrammingExercise exercise, GitService gitService) {
        var templateCommitHash = getCommitHash(exercise.getVcsTemplateRepositoryUri(), gitService);
        var solutionCommitHash = getCommitHash(exercise.getVcsSolutionRepositoryUri(), gitService);
        var testsCommitHash = getCommitHash(exercise.getVcsTestRepositoryUri(), gitService);

        Map<Long, String> auxiliaryRepositoryCommitHashes = new HashMap<>();
        for (AuxiliaryRepository repository : exercise.getAuxiliaryRepositories()) {
            var commitHash = getCommitHash(repository.getVcsRepositoryUri(), gitService);
            if (commitHash != null) {
                auxiliaryRepositoryCommitHashes.put(repository.getId(), commitHash);
            }
        }
        return new ProgrammingExerciseSnapshotDTO.CommitHashesDTO(templateCommitHash, solutionCommitHash, testsCommitHash, auxiliaryRepositoryCommitHashes);
    }

    @Nullable
    private static String getCommitHash(@Nullable LocalVCRepositoryUri uri, GitService gitService) {
        if (uri == null) {
            return null;
        }
        try {
            return gitService.getLastCommitHash(uri);
        }
        catch (Exception e) {
            log.warn("Could not retrieve the last commit hash for repoUri {} in ExerciseSnapshot", uri);
            return null;
        }
    }
}
