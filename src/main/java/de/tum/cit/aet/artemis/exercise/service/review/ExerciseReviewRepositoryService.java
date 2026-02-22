package de.tum.cit.aet.artemis.exercise.service.review;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

/**
 * Service for review-comment repository actions such as repository URI resolution and repository file existence validation.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseReviewRepositoryService {

    private static final String THREAD_ENTITY_NAME = "exerciseReviewCommentThread";

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final GitService gitService;

    public ExerciseReviewRepositoryService(ProgrammingExerciseRepository programmingExerciseRepository, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository,
            GitService gitService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.gitService = gitService;
    }

    /**
     * Resolves the latest commit SHA for the repository associated with a review-thread target.
     *
     * @param targetType            the thread target type (repository-based only)
     * @param auxiliaryRepositoryId the auxiliary repository id when {@code targetType} is {@code AUXILIARY_REPO}
     * @param exerciseId            the exercise id
     * @return the latest commit SHA, or {@code null} for problem-statement threads or missing repository URIs
     * @throws BadRequestAlertException if the exercise is not a programming exercise
     */
    public String resolveLatestCommitSha(CommentThreadLocationType targetType, Long auxiliaryRepositoryId, long exerciseId) {
        if (targetType == CommentThreadLocationType.PROBLEM_STATEMENT) {
            return null;
        }

        ProgrammingExercise exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(exerciseId)
                .orElseThrow(() -> new BadRequestAlertException("Exercise is not a programming exercise", THREAD_ENTITY_NAME, "exerciseNotProgramming"));

        LocalVCRepositoryUri repositoryUri = switch (targetType) {
            case TEMPLATE_REPO -> exercise.getVcsTemplateRepositoryUri() != null ? exercise.getVcsTemplateRepositoryUri()
                    : exercise.getTemplateParticipation() != null ? exercise.getTemplateParticipation().getVcsRepositoryUri() : null;
            case SOLUTION_REPO -> exercise.getVcsSolutionRepositoryUri() != null ? exercise.getVcsSolutionRepositoryUri()
                    : exercise.getSolutionParticipation() != null ? exercise.getSolutionParticipation().getVcsRepositoryUri() : null;
            case TEST_REPO -> exercise.getVcsTestRepositoryUri();
            case AUXILIARY_REPO -> getAuxiliaryRepositoryUri(auxiliaryRepositoryId, exerciseId);
            case PROBLEM_STATEMENT -> null;
        };

        if (repositoryUri == null) {
            return null;
        }

        return gitService.getLastCommitHash(repositoryUri);
    }

    /**
     * Resolves repository URIs for repository-backed review-thread targets.
     *
     * @param exerciseId the exercise id
     * @return repository URI lookups for supported repository-backed targets, including auxiliary repositories by id
     */
    public ConsistencyTargetRepositoryUris resolveTargetRepositoryUris(long exerciseId) {
        var programmingExerciseOpt = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(exerciseId);
        if (programmingExerciseOpt.isEmpty()) {
            return new ConsistencyTargetRepositoryUris(Map.of(), Map.of());
        }

        ProgrammingExercise programmingExercise = programmingExerciseOpt.get();
        Map<CommentThreadLocationType, LocalVCRepositoryUri> repositoryUris = new EnumMap<>(CommentThreadLocationType.class);
        Map<Long, LocalVCRepositoryUri> auxiliaryRepositoryUrisById = new HashMap<>();

        LocalVCRepositoryUri templateUri = programmingExercise.getVcsTemplateRepositoryUri() != null ? programmingExercise.getVcsTemplateRepositoryUri()
                : programmingExercise.getTemplateParticipation() != null ? programmingExercise.getTemplateParticipation().getVcsRepositoryUri() : null;
        if (templateUri != null) {
            repositoryUris.put(CommentThreadLocationType.TEMPLATE_REPO, templateUri);
        }

        LocalVCRepositoryUri solutionUri = programmingExercise.getVcsSolutionRepositoryUri() != null ? programmingExercise.getVcsSolutionRepositoryUri()
                : programmingExercise.getSolutionParticipation() != null ? programmingExercise.getSolutionParticipation().getVcsRepositoryUri() : null;
        if (solutionUri != null) {
            repositoryUris.put(CommentThreadLocationType.SOLUTION_REPO, solutionUri);
        }

        LocalVCRepositoryUri testUri = programmingExercise.getVcsTestRepositoryUri();
        if (testUri != null) {
            repositoryUris.put(CommentThreadLocationType.TEST_REPO, testUri);
        }

        for (AuxiliaryRepository auxiliaryRepository : programmingExercise.getAuxiliaryRepositories()) {
            if (auxiliaryRepository.getId() != null && auxiliaryRepository.getVcsRepositoryUri() != null) {
                auxiliaryRepositoryUrisById.put(auxiliaryRepository.getId(), auxiliaryRepository.getVcsRepositoryUri());
            }
        }

        return new ConsistencyTargetRepositoryUris(Map.copyOf(repositoryUris), Map.copyOf(auxiliaryRepositoryUrisById));
    }

    /**
     * Validates that a repository-relative path exists as a regular file in the configured default branch of a bare repository target.
     *
     * @param targetType             repository-backed thread target type
     * @param auxiliaryRepositoryId  auxiliary repository id when {@code targetType} is {@code AUXILIARY_REPO}
     * @param filePath               repository-relative file path using forward slashes
     * @param repositoryUrisByTarget repository URI lookups for consistency-check targets
     * @return empty if valid; otherwise an error message describing why validation failed
     */
    public Optional<String> validateFileExists(CommentThreadLocationType targetType, @Nullable Long auxiliaryRepositoryId, String filePath,
            ConsistencyTargetRepositoryUris repositoryUrisByTarget) {
        LocalVCRepositoryUri repositoryUri = findConsistencyTargetRepositoryUri(targetType, auxiliaryRepositoryId, repositoryUrisByTarget);
        if (repositoryUri == null) {
            return Optional.of("repository URI for " + targetType + " is missing");
        }

        try (var repository = gitService.getBareRepository(repositoryUri, false); RevWalk revWalk = new RevWalk(repository)) {
            ObjectId defaultBranchCommitId = repository.resolve(Constants.R_HEADS + defaultBranch);
            if (defaultBranchCommitId == null) {
                return Optional.of("default branch '" + defaultBranch + "' is missing in " + targetType);
            }

            RevCommit commit = revWalk.parseCommit(defaultBranchCommitId);
            try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, commit.getTree())) {
                boolean exists = treeWalk != null && treeWalk.getFileMode(0).getObjectType() == Constants.OBJ_BLOB;
                return exists ? Optional.empty() : Optional.of("file '" + filePath + "' does not exist in " + targetType);
            }
        }
        catch (Exception ex) {
            return Optional.of("file existence check failed for '" + filePath + "' in " + targetType + ": " + ex.getMessage());
        }
    }

    /**
     * Resolves the repository URI for a consistency-check target.
     * Auxiliary repository targets are resolved by id.
     *
     * @param targetType             repository-backed thread target type
     * @param auxiliaryRepositoryId  auxiliary repository id when {@code targetType} is {@code AUXILIARY_REPO}
     * @param repositoryUrisByTarget resolved consistency target repository URIs
     * @return resolved repository URI, or {@code null} if unavailable
     */
    @Nullable
    private LocalVCRepositoryUri findConsistencyTargetRepositoryUri(CommentThreadLocationType targetType, @Nullable Long auxiliaryRepositoryId,
            ConsistencyTargetRepositoryUris repositoryUrisByTarget) {
        return switch (targetType) {
            case TEMPLATE_REPO, SOLUTION_REPO, TEST_REPO -> repositoryUrisByTarget.repositoryUrisByTargetType().get(targetType);
            case AUXILIARY_REPO -> auxiliaryRepositoryId != null ? repositoryUrisByTarget.auxiliaryRepositoryUrisById().get(auxiliaryRepositoryId) : null;
            case PROBLEM_STATEMENT -> null;
        };
    }

    /**
     * Resolves the LocalVC repository URI for the given auxiliary repository id and exercise.
     *
     * @param auxiliaryRepositoryId the auxiliary repository id
     * @param exerciseId            the exercise id
     * @return the resolved repository URI
     */
    private LocalVCRepositoryUri getAuxiliaryRepositoryUri(Long auxiliaryRepositoryId, long exerciseId) {
        if (auxiliaryRepositoryId == null) {
            throw new BadRequestAlertException("Auxiliary repository id is required", THREAD_ENTITY_NAME, "auxiliaryRepositoryMissing");
        }

        AuxiliaryRepository auxiliaryRepository = auxiliaryRepositoryRepository.findById(auxiliaryRepositoryId)
                .orElseThrow(() -> new EntityNotFoundException("AuxiliaryRepository", auxiliaryRepositoryId));
        if (auxiliaryRepository.getExercise() == null || auxiliaryRepository.getExercise().getId() == null
                || !Objects.equals(auxiliaryRepository.getExercise().getId(), exerciseId)) {
            throw new BadRequestAlertException("Auxiliary repository does not belong to exercise", THREAD_ENTITY_NAME, "auxiliaryRepositoryMismatch");
        }
        return auxiliaryRepository.getVcsRepositoryUri();
    }

    /**
     * Container for repository URI lookups used during consistency-check issue mapping.
     *
     * @param repositoryUrisByTargetType  repository URI lookup by repository-backed thread target type
     * @param auxiliaryRepositoryUrisById repository URI lookup for auxiliary repositories by auxiliary repository id
     */
    public record ConsistencyTargetRepositoryUris(Map<CommentThreadLocationType, LocalVCRepositoryUri> repositoryUrisByTargetType,
            Map<Long, LocalVCRepositoryUri> auxiliaryRepositoryUrisById) {
    }
}
