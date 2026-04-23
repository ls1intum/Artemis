package de.tum.cit.aet.artemis.exercise.service.review.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadGroup;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import de.tum.cit.aet.artemis.exercise.dto.review.ConsistencyIssueCommentContentDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.CreateCommentThreadGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.UserCommentContentDTO;
import de.tum.cit.aet.artemis.hyperion.domain.ArtifactType;
import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class ExerciseReviewValidationUtilTest {

    private static final String THREAD_ENTITY = "thread";

    private static final String COMMENT_ENTITY = "comment";

    private static final String GROUP_ENTITY = "group";

    @Test
    void shouldNotThrowWhenExerciseIdMatchesRequest() {
        assertThatCode(() -> ExerciseReviewValidationUtil.validateExerciseIdMatchesRequest(1L, 1L, THREAD_ENTITY)).doesNotThrowAnyException();
    }

    @Test
    void shouldThrowBadRequestWhenExerciseIdDoesNotMatchRequest() {
        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateExerciseIdMatchesRequest(1L, 2L, THREAD_ENTITY)).isInstanceOf(BadRequestAlertException.class)
                .extracting("errorKey").isEqualTo("exerciseMismatch");
    }

    @Test
    void shouldNotThrowWhenUserContentMatchesUserCommentType() {
        assertThatCode(() -> ExerciseReviewValidationUtil.validateContentMatchesType(CommentType.USER, new UserCommentContentDTO("Looks good"), COMMENT_ENTITY))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowWhenConsistencyContentMatchesConsistencyCommentType() {
        var content = new ConsistencyIssueCommentContentDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "Issue text", null);
        assertThatCode(() -> ExerciseReviewValidationUtil.validateContentMatchesType(CommentType.CONSISTENCY_CHECK, content, COMMENT_ENTITY)).doesNotThrowAnyException();
    }

    @Test
    void shouldThrowBadRequestWhenCommentTypeOrContentIsNull() {
        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateContentMatchesType(null, new UserCommentContentDTO("x"), COMMENT_ENTITY))
                .isInstanceOf(BadRequestAlertException.class).extracting("errorKey").isEqualTo("contentOrTypeMissing");
        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateContentMatchesType(CommentType.USER, null, COMMENT_ENTITY)).isInstanceOf(BadRequestAlertException.class)
                .extracting("errorKey").isEqualTo("contentOrTypeMissing");
    }

    @Test
    void shouldThrowBadRequestWhenCommentContentDoesNotMatchType() {
        var consistencyContent = new ConsistencyIssueCommentContentDTO(Severity.MEDIUM, ConsistencyIssueCategory.ATTRIBUTE_TYPE_MISMATCH, "Issue text", null);
        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateContentMatchesType(CommentType.USER, consistencyContent, COMMENT_ENTITY))
                .isInstanceOf(BadRequestAlertException.class).extracting("errorKey").isEqualTo("contentTypeMismatch");
    }

    @Test
    void shouldThrowBadRequestWhenUserCommentPayloadIsNull() {
        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateUserCommentPayload(null, COMMENT_ENTITY)).isInstanceOf(BadRequestAlertException.class).extracting("errorKey")
                .isEqualTo("contentMissing");
    }

    @Test
    void shouldThrowBadRequestWhenResolvedStatePayloadIsNull() {
        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateResolvedStatePayload(null, THREAD_ENTITY)).isInstanceOf(BadRequestAlertException.class).extracting("errorKey")
                .isEqualTo("bodyMissing");
    }

    @Test
    void shouldNotThrowWhenThreadPayloadForRepositoryThreadIsValid() {
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(CommentThreadLocationType.TEMPLATE_REPO, null, "src/main/java/App.java", 3, new UserCommentContentDTO("Initial"));

        assertThatCode(() -> ExerciseReviewValidationUtil.validateThreadPayload(dto, THREAD_ENTITY)).doesNotThrowAnyException();
    }

    @Test
    void shouldThrowBadRequestWhenThreadPayloadContainsInvalidPathTraversal() {
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(CommentThreadLocationType.TEMPLATE_REPO, null, "src/../App.java", 3, new UserCommentContentDTO("Initial"));

        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateThreadPayload(dto, THREAD_ENTITY)).isInstanceOf(BadRequestAlertException.class).extracting("errorKey")
                .isEqualTo("initialFilePathInvalid");
    }

    @Test
    void shouldThrowBadRequestWhenProblemStatementThreadContainsFilePath() {
        CreateCommentThreadDTO dto = new CreateCommentThreadDTO(CommentThreadLocationType.PROBLEM_STATEMENT, null, "problem_statement.md", 1, new UserCommentContentDTO("Initial"));

        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateThreadPayload(dto, THREAD_ENTITY)).isInstanceOf(BadRequestAlertException.class).extracting("errorKey")
                .isEqualTo("initialFilePathNotAllowed");
    }

    @Test
    void shouldThrowBadRequestWhenAuxiliaryRepositoryIdConstraintsAreViolated() {
        CreateCommentThreadDTO auxiliaryMissingId = new CreateCommentThreadDTO(CommentThreadLocationType.AUXILIARY_REPO, null, "src/main/java/App.java", 1,
                new UserCommentContentDTO("Initial"));
        CreateCommentThreadDTO nonAuxiliaryWithId = new CreateCommentThreadDTO(CommentThreadLocationType.TEMPLATE_REPO, 42L, "src/main/java/App.java", 1,
                new UserCommentContentDTO("Initial"));

        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateThreadPayload(auxiliaryMissingId, THREAD_ENTITY)).isInstanceOf(BadRequestAlertException.class)
                .extracting("errorKey").isEqualTo("auxiliaryRepositoryMissing");
        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateThreadPayload(nonAuxiliaryWithId, THREAD_ENTITY)).isInstanceOf(BadRequestAlertException.class)
                .extracting("errorKey").isEqualTo("auxiliaryRepositoryNotAllowed");
    }

    @Test
    void shouldValidateGroupPayloadForValidAndInvalidCases() {
        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateGroupPayload(null, GROUP_ENTITY)).isInstanceOf(BadRequestAlertException.class).extracting("errorKey")
                .isEqualTo("bodyMissing");
        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateGroupPayload(new CreateCommentThreadGroupDTO(List.of(1L)), GROUP_ENTITY))
                .isInstanceOf(BadRequestAlertException.class).extracting("errorKey").isEqualTo("threadCountTooLow");

        List<Long> threadIds = ExerciseReviewValidationUtil.validateGroupPayload(new CreateCommentThreadGroupDTO(List.of(1L, 2L)), GROUP_ENTITY);
        assertThat(threadIds).containsExactly(1L, 2L);
    }

    @Test
    void shouldValidateGroupThreadsForInvalidAndValidCases() {
        CommentThread validThread = createThread(10L, false);
        CommentThread mismatchingExerciseThread = createThread(11L, false);
        CommentThread groupedThread = createThread(10L, true);

        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateGroupThreads(List.of(validThread), 2, 10L, THREAD_ENTITY)).isInstanceOf(BadRequestAlertException.class)
                .extracting("errorKey").isEqualTo("threadMissing");
        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateGroupThreads(List.of(mismatchingExerciseThread), 1, 10L, THREAD_ENTITY))
                .isInstanceOf(BadRequestAlertException.class).extracting("errorKey").isEqualTo("exerciseMismatch");
        assertThatThrownBy(() -> ExerciseReviewValidationUtil.validateGroupThreads(List.of(groupedThread), 1, 10L, THREAD_ENTITY)).isInstanceOf(BadRequestAlertException.class)
                .extracting("errorKey").isEqualTo("threadGrouped");

        assertThatCode(() -> ExerciseReviewValidationUtil.validateGroupThreads(List.of(validThread), 1, 10L, THREAD_ENTITY)).doesNotThrowAnyException();
    }

    @Test
    void shouldReturnEmptyValidationResultWhenConsistencyIssueIsValid() {
        ConsistencyIssueDTO validRepositoryIssue = new ConsistencyIssueDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "Description", "Fix",
                List.of(new ArtifactLocationDTO(ArtifactType.TEMPLATE_REPOSITORY, "src/main/java/App.java", 1, 2)));
        ConsistencyIssueDTO validProblemStatementIssue = new ConsistencyIssueDTO(Severity.MEDIUM, ConsistencyIssueCategory.ATTRIBUTE_TYPE_MISMATCH, "Description", "Fix",
                List.of(new ArtifactLocationDTO(ArtifactType.PROBLEM_STATEMENT, "", 3, 3)));

        assertThat(ExerciseReviewValidationUtil.validateConsistencyIssue(validRepositoryIssue)).isEmpty();
        assertThat(ExerciseReviewValidationUtil.validateConsistencyIssue(validProblemStatementIssue)).isEmpty();
    }

    @Test
    void shouldReturnValidationErrorWhenConsistencyIssueTopLevelFieldsAreInvalid() {
        assertThat(ExerciseReviewValidationUtil.validateConsistencyIssue(null)).contains("issue is null");
        assertThat(ExerciseReviewValidationUtil.validateConsistencyIssue(new ConsistencyIssueDTO(null, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "Description", "Fix",
                List.of(new ArtifactLocationDTO(ArtifactType.TEMPLATE_REPOSITORY, "src/main/java/App.java", 1, 1))))).contains("severity is null");
        assertThat(ExerciseReviewValidationUtil.validateConsistencyIssue(new ConsistencyIssueDTO(Severity.HIGH, null, "Description", "Fix",
                List.of(new ArtifactLocationDTO(ArtifactType.TEMPLATE_REPOSITORY, "src/main/java/App.java", 1, 1))))).contains("category is null");
        assertThat(ExerciseReviewValidationUtil.validateConsistencyIssue(new ConsistencyIssueDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "", "Fix",
                List.of(new ArtifactLocationDTO(ArtifactType.TEMPLATE_REPOSITORY, "src/main/java/App.java", 1, 1))))).contains("description is missing or blank");
        assertThat(ExerciseReviewValidationUtil.validateConsistencyIssue(new ConsistencyIssueDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "Description",
                null, List.of(new ArtifactLocationDTO(ArtifactType.TEMPLATE_REPOSITORY, "src/main/java/App.java", 1, 1))))).contains("suggestedFix is null");
        assertThat(ExerciseReviewValidationUtil
                .validateConsistencyIssue(new ConsistencyIssueDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "Description", "Fix", null)))
                .contains("relatedLocations is null");
        assertThat(ExerciseReviewValidationUtil
                .validateConsistencyIssue(new ConsistencyIssueDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "Description", "Fix", List.of())))
                .contains("relatedLocations is empty");
    }

    @Test
    void shouldReturnDetailedValidationErrorWhenConsistencyIssueLocationsAreInvalid() {
        ConsistencyIssueDTO nullLocationIssue = new ConsistencyIssueDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "Description", "Fix",
                Collections.singletonList(null));
        assertThat(ExerciseReviewValidationUtil.validateConsistencyIssue(nullLocationIssue)).contains("relatedLocations[0] is null");

        ConsistencyIssueDTO invalidLineRangeIssue = new ConsistencyIssueDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "Description", "Fix",
                List.of(new ArtifactLocationDTO(ArtifactType.TEMPLATE_REPOSITORY, "src/main/java/App.java", 5, 2)));
        assertThat(ExerciseReviewValidationUtil.validateConsistencyIssue(invalidLineRangeIssue)).contains("relatedLocations[0] endLine must be >= startLine");

        ConsistencyIssueDTO blankRepositoryPathIssue = new ConsistencyIssueDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "Description", "Fix",
                List.of(new ArtifactLocationDTO(ArtifactType.TEMPLATE_REPOSITORY, "", 1, 1)));
        assertThat(ExerciseReviewValidationUtil.validateConsistencyIssue(blankRepositoryPathIssue)).contains("relatedLocations[0] filePath must not be blank");

        ConsistencyIssueDTO invalidPathIssue = new ConsistencyIssueDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "Description", "Fix",
                List.of(new ArtifactLocationDTO(ArtifactType.TEMPLATE_REPOSITORY, "src/../App.java", 1, 1)));
        assertThat(ExerciseReviewValidationUtil.validateConsistencyIssue(invalidPathIssue)).contains("relatedLocations[0] filePath is invalid");

        ConsistencyIssueDTO tooLongPathIssue = new ConsistencyIssueDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_PARAMETER_MISMATCH, "Description", "Fix",
                List.of(new ArtifactLocationDTO(ArtifactType.TEMPLATE_REPOSITORY, "a".repeat(1025), 1, 1)));
        assertThat(ExerciseReviewValidationUtil.validateConsistencyIssue(tooLongPathIssue)).contains("relatedLocations[0] filePath must not exceed 1024 characters");
    }

    private static CommentThread createThread(long exerciseId, boolean grouped) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);

        CommentThread thread = new CommentThread();
        thread.setExercise(exercise);
        if (grouped) {
            thread.setGroup(new CommentThreadGroup());
        }
        return thread;
    }
}
