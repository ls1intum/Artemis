import { CommentThread, CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import type { ReviewCommentDraftLocation } from 'app/exercise/review/review-comment.store';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

/**
 * Returns the current thread file path, falling back to the initial file path.
 *
 * @param thread The review comment thread.
 * @returns Current file path or the initial fallback path.
 */
export function getThreadFilePath(thread: CommentThread): string | undefined {
    return thread.filePath ?? thread.initialFilePath;
}

/**
 * Returns the 0-based Monaco line for a thread, falling back to initial location.
 *
 * @param thread The review comment thread.
 * @returns 0-based line index.
 */
export function getReviewThreadLine(thread: CommentThread): number {
    return (thread.lineNumber ?? thread.initialLineNumber ?? 1) - 1;
}

/**
 * Builds a problem-statement draft location.
 *
 * @param lineNumber The 1-based line number.
 * @returns Draft location for problem-statement comments.
 */
export function buildProblemStatementDraftLocation(lineNumber: number): ReviewCommentDraftLocation {
    return {
        targetType: CommentThreadLocationType.PROBLEM_STATEMENT,
        lineNumber,
    };
}

/**
 * Checks whether a thread targets the problem statement.
 *
 * @param thread The review comment thread.
 * @returns True when the thread belongs to the problem statement.
 */
export function isProblemStatementThread(thread: CommentThread): boolean {
    return thread.targetType === CommentThreadLocationType.PROBLEM_STATEMENT;
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

/**
 * Builds a repository-scoped draft location for review comments.
 *
 * @param repositoryType The selected repository type.
 * @param fileName The selected file path.
 * @param lineNumber The 1-based line number.
 * @param auxiliaryRepositoryId Selected auxiliary repository id for auxiliary repositories.
 * @returns Draft location for the selected repository or undefined when unsupported.
 */
export function buildRepositoryDraftLocation(
    repositoryType: RepositoryType | undefined,
    fileName: string,
    lineNumber: number,
    auxiliaryRepositoryId?: number,
): ReviewCommentDraftLocation | undefined {
    if (!repositoryType) {
        return undefined;
    }
    const targetType = mapRepositoryToThreadLocationType(repositoryType);
    if (!targetType) {
        return undefined;
    }
    return {
        targetType,
        filePath: fileName,
        lineNumber,
        auxiliaryRepositoryId: repositoryType === RepositoryType.AUXILIARY ? auxiliaryRepositoryId : undefined,
    };
}
