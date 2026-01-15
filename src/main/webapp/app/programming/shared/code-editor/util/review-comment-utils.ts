import { CommentThread, CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

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
