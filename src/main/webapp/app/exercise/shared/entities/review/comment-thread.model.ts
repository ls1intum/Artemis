import { Comment, CreateComment } from 'app/exercise/shared/entities/review/comment.model';

export enum CommentThreadLocationType {
    PROBLEM_STATEMENT = 'PROBLEM_STATEMENT',
    TEMPLATE_REPO = 'TEMPLATE_REPO',
    SOLUTION_REPO = 'SOLUTION_REPO',
    TEST_REPO = 'TEST_REPO',
    AUXILIARY_REPO = 'AUXILIARY_REPO',
}

export interface CommentThread {
    id: number;
    groupId?: number;
    exerciseId: number;
    targetType: CommentThreadLocationType;
    auxiliaryRepositoryId?: number;
    initialVersionId?: number;
    initialCommitSha?: string;
    filePath?: string;
    initialFilePath?: string;
    lineNumber?: number;
    initialLineNumber: number;
    outdated: boolean;
    resolved: boolean;
    comments: Comment[];
}

export interface CreateCommentThread {
    targetType: CommentThreadLocationType;
    auxiliaryRepositoryId?: number;
    initialFilePath?: string;
    initialLineNumber: number;
    initialComment: CreateComment;
}

export interface UpdateThreadResolvedState {
    resolved: boolean;
}
