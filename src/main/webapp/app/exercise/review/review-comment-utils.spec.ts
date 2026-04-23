import {
    getFirstCommentByCreatedDateThenId,
    isReviewCommentsSupportedRepository,
    mapRepositoryToThreadLocationType,
    matchesSelectedRepository,
    sortCommentsByCreatedDateThenId,
} from 'app/exercise/review/review-comment-utils';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { afterEach, describe, expect, it, vi } from 'vitest';

describe('matchesSelectedRepository', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should match template repository', () => {
        const thread = { targetType: CommentThreadLocationType.TEMPLATE_REPO } as any;
        expect(matchesSelectedRepository(thread, RepositoryType.TEMPLATE)).toBe(true);
    });

    it('should match solution repository', () => {
        const thread = { targetType: CommentThreadLocationType.SOLUTION_REPO } as any;
        expect(matchesSelectedRepository(thread, RepositoryType.SOLUTION)).toBe(true);
    });

    it('should match test repository', () => {
        const thread = { targetType: CommentThreadLocationType.TEST_REPO } as any;
        expect(matchesSelectedRepository(thread, RepositoryType.TESTS)).toBe(true);
    });

    it('should match auxiliary repository with matching id', () => {
        const thread = { targetType: CommentThreadLocationType.AUXILIARY_REPO, auxiliaryRepositoryId: 4 } as any;
        expect(matchesSelectedRepository(thread, RepositoryType.AUXILIARY, 4)).toBe(true);
    });

    it('should reject auxiliary repository when id mismatches', () => {
        const thread = { targetType: CommentThreadLocationType.AUXILIARY_REPO, auxiliaryRepositoryId: 2 } as any;
        expect(matchesSelectedRepository(thread, RepositoryType.AUXILIARY, 3)).toBe(false);
    });

    it('should accept auxiliary repository when no id is provided', () => {
        const thread = { targetType: CommentThreadLocationType.AUXILIARY_REPO, auxiliaryRepositoryId: 2 } as any;
        expect(matchesSelectedRepository(thread, RepositoryType.AUXILIARY)).toBe(true);
    });

    it('should return false for unknown repository type', () => {
        const thread = { targetType: CommentThreadLocationType.TEMPLATE_REPO } as any;
        expect(matchesSelectedRepository(thread, undefined)).toBe(false);
    });
});

describe('mapRepositoryToThreadLocationType', () => {
    it('should map solution repo', () => {
        expect(mapRepositoryToThreadLocationType(RepositoryType.SOLUTION)).toBe(CommentThreadLocationType.SOLUTION_REPO);
    });

    it('should map test repo', () => {
        expect(mapRepositoryToThreadLocationType(RepositoryType.TESTS)).toBe(CommentThreadLocationType.TEST_REPO);
    });

    it('should map auxiliary repo', () => {
        expect(mapRepositoryToThreadLocationType(RepositoryType.AUXILIARY)).toBe(CommentThreadLocationType.AUXILIARY_REPO);
    });

    it('should map template repo', () => {
        expect(mapRepositoryToThreadLocationType(RepositoryType.TEMPLATE)).toBe(CommentThreadLocationType.TEMPLATE_REPO);
    });

    it('should return undefined for unsupported repository type', () => {
        expect(mapRepositoryToThreadLocationType(RepositoryType.ASSIGNMENT)).toBeUndefined();
    });
});

describe('isReviewCommentsSupportedRepository', () => {
    it('should support template repository', () => {
        expect(isReviewCommentsSupportedRepository(RepositoryType.TEMPLATE)).toBe(true);
    });

    it('should support solution repository', () => {
        expect(isReviewCommentsSupportedRepository(RepositoryType.SOLUTION)).toBe(true);
    });

    it('should support tests repository', () => {
        expect(isReviewCommentsSupportedRepository(RepositoryType.TESTS)).toBe(true);
    });

    it('should support auxiliary repository', () => {
        expect(isReviewCommentsSupportedRepository(RepositoryType.AUXILIARY)).toBe(true);
    });

    it('should not support assignment repository', () => {
        expect(isReviewCommentsSupportedRepository(RepositoryType.ASSIGNMENT)).toBe(false);
    });

    it('should not support undefined repository', () => {
        expect(isReviewCommentsSupportedRepository(undefined)).toBe(false);
    });
});

describe('sortCommentsByCreatedDateThenId', () => {
    it('should sort comments by createdDate and id', () => {
        const comments = [
            { id: 4, createdDate: '2024-01-02T00:00:00Z' },
            { id: 1, createdDate: '2024-01-01T00:00:00Z' },
            { id: 3, createdDate: '2024-01-02T00:00:00Z' },
        ] as any;

        const sorted = sortCommentsByCreatedDateThenId(comments);

        expect(sorted.map((comment: any) => comment.id)).toEqual([1, 3, 4]);
    });

    it('should return empty list for undefined comments', () => {
        expect(sortCommentsByCreatedDateThenId(undefined)).toEqual([]);
    });
});

describe('getFirstCommentByCreatedDateThenId', () => {
    it('should return the first chronological comment', () => {
        const comments = [
            { id: 5, createdDate: '2024-01-02T00:00:00Z' },
            { id: 2, createdDate: '2024-01-01T00:00:00Z' },
        ] as any;

        const firstComment = getFirstCommentByCreatedDateThenId(comments);

        expect(firstComment?.id).toBe(2);
    });

    it('should return undefined for empty comments', () => {
        expect(getFirstCommentByCreatedDateThenId([] as any)).toBeUndefined();
    });
});
