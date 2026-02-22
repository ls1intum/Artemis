package de.tum.cit.aet.artemis.exercise.service.review.validation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.ConsistencyIssueCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UpdateThreadResolvedStateDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.hyperion.domain.ArtifactType;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;

/**
 * Centralized validation utilities for exercise review service operations.
 */
public final class ExerciseReviewValidationUtil {

    private static final int MAX_FILE_PATH_LENGTH = 1024;

    private ExerciseReviewValidationUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Ensures the exercise id in the request path matches the exercise of the target entity.
     *
     * @param requestExerciseId the exercise id from the URL path
     * @param entityExerciseId  the exercise id of the loaded entity
     * @param entityName        the entity name used in the API error payload
     */
    public static void validateExerciseIdMatchesRequest(long requestExerciseId, Long entityExerciseId, String entityName) {
        if (!Objects.equals(requestExerciseId, entityExerciseId)) {
            throw new BadRequestAlertException("Entity exercise does not match request", entityName, "exerciseMismatch");
        }
    }

    /**
     * Validates that the comment content matches the given comment type.
     *
     * @param type              the comment type to validate against
     * @param content           the content payload to validate
     * @param commentEntityName the entity name used in the API error payload
     */
    public static void validateContentMatchesType(CommentType type, CommentContentDTO content, String commentEntityName) {
        if (type == null || content == null) {
            throw new BadRequestAlertException("Comment content and type must be set", commentEntityName, "contentOrTypeMissing");
        }

        boolean isValid = switch (type) {
            case USER -> content instanceof UserCommentContentDTO;
            case CONSISTENCY_CHECK -> content instanceof ConsistencyIssueCommentContentDTO;
        };

        if (!isValid) {
            throw new BadRequestAlertException("Comment content does not match type", commentEntityName, "contentTypeMismatch");
        }
    }

    /**
     * Validates a user-comment request payload.
     *
     * @param dto               the user comment content payload
     * @param commentEntityName the entity name used in the API error payload
     */
    public static void validateUserCommentPayload(UserCommentContentDTO dto, String commentEntityName) {
        if (dto == null) {
            throw new BadRequestAlertException("Comment content must be set", commentEntityName, "contentMissing");
        }
    }

    /**
     * Validates the payload for thread resolved-state updates.
     *
     * @param dto              the update payload
     * @param threadEntityName the entity name used in the API error payload
     */
    public static void validateResolvedStatePayload(UpdateThreadResolvedStateDTO dto, String threadEntityName) {
        if (dto == null) {
            throw new BadRequestAlertException("Request body must be set", threadEntityName, "bodyMissing");
        }
    }

    /**
     * Validates thread payload invariants based on target type (problem statement vs repository targets).
     *
     * @param dto              the thread creation payload to validate
     * @param threadEntityName the entity name used in the API error payload
     */
    public static void validateThreadPayload(CreateCommentThreadDTO dto, String threadEntityName) {
        if (dto == null) {
            throw new BadRequestAlertException("Request body must be set", threadEntityName, "bodyMissing");
        }
        if (dto.targetType() == null) {
            throw new BadRequestAlertException("Thread target type must be set", threadEntityName, "targetTypeMissing");
        }
        if (dto.initialLineNumber() == null) {
            throw new BadRequestAlertException("Initial line number must be set", threadEntityName, "initialLineNumberMissing");
        }
        if (dto.initialLineNumber() < 1) {
            throw new BadRequestAlertException("Initial line number must be at least 1", threadEntityName, "initialLineNumberInvalid");
        }
        if (dto.initialComment() == null) {
            throw new BadRequestAlertException("Initial comment must be set", threadEntityName, "initialCommentMissing");
        }
        if (dto.targetType() != CommentThreadLocationType.PROBLEM_STATEMENT && dto.initialFilePath() == null) {
            throw new BadRequestAlertException("Initial file path is required for repository threads", threadEntityName, "initialFilePathMissing");
        }
        if (dto.targetType() == CommentThreadLocationType.PROBLEM_STATEMENT && dto.initialFilePath() != null) {
            throw new BadRequestAlertException("Initial file path is not allowed for problem statement threads", threadEntityName, "initialFilePathNotAllowed");
        }
        if (dto.targetType() != CommentThreadLocationType.PROBLEM_STATEMENT) {
            if (dto.initialFilePath().isBlank()) {
                throw new BadRequestAlertException("Initial file path must not be blank for repository threads", threadEntityName, "initialFilePathBlank");
            }
            if (dto.initialFilePath().length() > MAX_FILE_PATH_LENGTH) {
                throw new BadRequestAlertException("Initial file path must not exceed 1024 characters", threadEntityName, "initialFilePathTooLong");
            }
            try {
                FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(dto.initialFilePath());
            }
            catch (IllegalArgumentException ex) {
                throw new BadRequestAlertException("Initial file path is invalid", threadEntityName, "initialFilePathInvalid");
            }
        }
        if (dto.targetType() != CommentThreadLocationType.AUXILIARY_REPO && dto.auxiliaryRepositoryId() != null) {
            throw new BadRequestAlertException("Auxiliary repository id is only allowed for auxiliary repository threads", threadEntityName, "auxiliaryRepositoryNotAllowed");
        }
        if (dto.targetType() == CommentThreadLocationType.AUXILIARY_REPO && dto.auxiliaryRepositoryId() == null) {
            throw new BadRequestAlertException("Auxiliary repository id is required for auxiliary repository threads", threadEntityName, "auxiliaryRepositoryMissing");
        }
    }

    /**
     * Validates thread-group creation payload and returns normalized thread ids.
     *
     * @param dto                   the group creation payload
     * @param threadGroupEntityName the group entity name used in the API error payload
     * @return the thread ids from the validated payload
     */
    public static List<Long> validateGroupPayload(CreateCommentThreadGroupDTO dto, String threadGroupEntityName) {
        if (dto == null) {
            throw new BadRequestAlertException("Request body must be set", threadGroupEntityName, "bodyMissing");
        }

        List<Long> threadIds = dto.threadIds();
        if (threadIds == null || threadIds.size() < 2) {
            throw new BadRequestAlertException("A thread group must contain at least two threads", threadGroupEntityName, "threadCountTooLow");
        }
        return threadIds;
    }

    /**
     * Validates thread-group membership constraints for the selected threads.
     *
     * @param threads          loaded thread entities
     * @param expectedCount    number of requested thread ids
     * @param exerciseId       expected exercise id for all threads
     * @param threadEntityName thread entity name used in the API error payload
     */
    public static void validateGroupThreads(List<CommentThread> threads, int expectedCount, long exerciseId, String threadEntityName) {
        if (threads.size() != expectedCount) {
            throw new BadRequestAlertException("Some threads do not exist", threadEntityName, "threadMissing");
        }

        for (CommentThread thread : threads) {
            if (thread.getExercise() == null || !Objects.equals(thread.getExercise().getId(), exerciseId)) {
                throw new BadRequestAlertException("Thread exercise does not match request", threadEntityName, "exerciseMismatch");
            }
            if (thread.getGroup() != null) {
                throw new BadRequestAlertException("Thread already belongs to another group", threadEntityName, "threadGrouped");
            }
        }
    }

    /**
     * Validates one consistency issue before persistence.
     *
     * @param issue the issue to validate
     * @return empty if valid; otherwise a reason why the issue is invalid
     */
    public static Optional<String> validateConsistencyIssue(ConsistencyIssueDTO issue) {
        if (issue == null) {
            return Optional.of("issue is null");
        }
        if (issue.severity() == null) {
            return Optional.of("severity is null");
        }
        if (issue.category() == null) {
            return Optional.of("category is null");
        }
        if (issue.description() == null || issue.description().isBlank()) {
            return Optional.of("description is missing or blank");
        }
        if (issue.suggestedFix() == null) {
            return Optional.of("suggestedFix is null");
        }
        if (issue.relatedLocations() == null) {
            return Optional.of("relatedLocations is null");
        }
        if (issue.relatedLocations().isEmpty()) {
            return Optional.of("relatedLocations is empty");
        }

        for (int index = 0; index < issue.relatedLocations().size(); index++) {
            Optional<String> locationValidationError = validateConsistencyIssueLocation(issue.relatedLocations().get(index));
            if (locationValidationError.isPresent()) {
                return Optional.of("relatedLocations[" + index + "] " + locationValidationError.get());
            }
        }
        return Optional.empty();
    }

    /**
     * Validates one consistency issue location before persistence.
     *
     * @param location the location to validate
     * @return empty if valid; otherwise a reason why the location is invalid
     */
    private static Optional<String> validateConsistencyIssueLocation(ArtifactLocationDTO location) {
        if (location == null) {
            return Optional.of("is null");
        }
        if (location.type() == null) {
            return Optional.of("type is null");
        }
        if (location.filePath() == null) {
            return Optional.of("filePath is null");
        }
        if (location.startLine() == null || location.startLine() < 1) {
            return Optional.of("startLine must be >= 1");
        }
        if (location.endLine() == null || location.endLine() < 1) {
            return Optional.of("endLine must be >= 1");
        }
        if (location.endLine() < location.startLine()) {
            return Optional.of("endLine must be >= startLine");
        }
        if (location.type() != ArtifactType.PROBLEM_STATEMENT) {
            if (location.filePath().isBlank()) {
                return Optional.of("filePath must not be blank");
            }
            if (location.filePath().length() > MAX_FILE_PATH_LENGTH) {
                return Optional.of("filePath must not exceed 1024 characters");
            }
            try {
                FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(location.filePath());
            }
            catch (IllegalArgumentException ex) {
                return Optional.of("filePath is invalid");
            }
        }
        return Optional.empty();
    }
}
