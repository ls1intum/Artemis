import { CommentThread, CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { Comment } from 'app/exercise/shared/entities/review/comment.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

/**
 * Sorts comments by creation timestamp and then by id for deterministic ordering.
 *
 * @param comments The comments to sort.
 * @returns A sorted copy of the provided comments.
 */
export function sortCommentsByCreatedDateThenId(comments: Comment[] | undefined): Comment[] {
    if (!comments?.length) {
        return [];
    }

    return [...comments].sort((a, b) => {
        const aDate = a.createdDate ? Date.parse(a.createdDate) : 0;
        const bDate = b.createdDate ? Date.parse(b.createdDate) : 0;
        if (aDate !== bDate) {
            return aDate - bDate;
        }
        return (a.id ?? 0) - (b.id ?? 0);
    });
}

/**
 * Returns the first comment according to chronological ordering by creation timestamp and id.
 *
 * @param comments The comments to inspect.
 * @returns The first chronological comment, if present.
 */
export function getFirstCommentByCreatedDateThenId(comments: Comment[] | undefined): Comment | undefined {
    return sortCommentsByCreatedDateThenId(comments)[0];
}

/**
 * Checks whether a thread belongs to the currently selected repository.
 *
 * @param thread The comment thread to check.
 * @param repositoryType The selected repository type.
 * @param auxiliaryRepositoryId The selected auxiliary repository id, if any.
 * @returns True if the thread matches the repository selection.
 */
export function matchesSelectedRepository(thread: CommentThread, repositoryType?: RepositoryType, auxiliaryRepositoryId?: number): boolean {
    switch (repositoryType) {
        case RepositoryType.SOLUTION:
            return thread.targetType === CommentThreadLocationType.SOLUTION_REPO;
        case RepositoryType.TESTS:
            return thread.targetType === CommentThreadLocationType.TEST_REPO;
        case RepositoryType.AUXILIARY: {
            if (thread.targetType !== CommentThreadLocationType.AUXILIARY_REPO) {
                return false;
            }
            if (auxiliaryRepositoryId === undefined) {
                return true;
            }
            return thread.auxiliaryRepositoryId === auxiliaryRepositoryId;
        }
        case RepositoryType.TEMPLATE:
            return thread.targetType === CommentThreadLocationType.TEMPLATE_REPO;
        default:
            return false;
    }
}

/**
 * Maps a repository type to the corresponding thread target type.
 *
 * @param repositoryType The repository type from the code editor.
 * @returns The matching comment thread location type.
 */
export function mapRepositoryToThreadLocationType(repositoryType: RepositoryType): CommentThreadLocationType | undefined {
    switch (repositoryType) {
        case RepositoryType.SOLUTION:
            return CommentThreadLocationType.SOLUTION_REPO;
        case RepositoryType.TESTS:
            return CommentThreadLocationType.TEST_REPO;
        case RepositoryType.AUXILIARY:
            return CommentThreadLocationType.AUXILIARY_REPO;
        case RepositoryType.TEMPLATE:
            return CommentThreadLocationType.TEMPLATE_REPO;
        default:
            return undefined;
    }
}

/**
 * Checks whether review comments are supported for the selected repository.
 *
 * @param repositoryType The repository type from the code editor.
 * @returns True if review comments are supported for this repository type.
 */
export function isReviewCommentsSupportedRepository(repositoryType?: RepositoryType): boolean {
    switch (repositoryType) {
        case RepositoryType.SOLUTION:
        case RepositoryType.TESTS:
        case RepositoryType.AUXILIARY:
        case RepositoryType.TEMPLATE:
            return true;
        default:
            return false;
    }
}
