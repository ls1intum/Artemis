import { CommentThread, CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

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
export function mapRepositoryToThreadLocationType(repositoryType: RepositoryType): CommentThreadLocationType {
    switch (repositoryType) {
        case RepositoryType.SOLUTION:
            return CommentThreadLocationType.SOLUTION_REPO;
        case RepositoryType.TESTS:
            return CommentThreadLocationType.TEST_REPO;
        case RepositoryType.AUXILIARY:
            return CommentThreadLocationType.AUXILIARY_REPO;
        default:
            return CommentThreadLocationType.TEMPLATE_REPO;
    }
}
