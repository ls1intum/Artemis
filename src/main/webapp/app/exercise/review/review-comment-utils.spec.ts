import {
    buildProblemStatementDraftLocation,
    buildRepositoryDraftLocation,
    getReviewThreadLine,
    getThreadFilePath,
    isProblemStatementThread,
    isReviewCommentsSupportedRepository,
    mapRepositoryToThreadLocationType,
    matchesSelectedRepository,
} from 'app/exercise/review/review-comment-utils';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { afterEach, describe, expect, it, vi } from 'vitest';

describe('review-comment-utils', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should fall back to initial file path when current file path is missing', () => {
        const thread = { initialFilePath: 'src/File.java', initialLineNumber: 7 } as any;
        expect(getThreadFilePath(thread)).toBe('src/File.java');
    });

    it('should prefer current file path over initial fallback', () => {
        const thread = { filePath: 'src/Current.java', initialFilePath: 'src/Initial.java', lineNumber: 4, initialLineNumber: 9 } as any;
        expect(getThreadFilePath(thread)).toBe('src/Current.java');
    });

    it('should compute 0-based line from initial line number fallback', () => {
        const thread = { initialLineNumber: 7 } as any;
        expect(getReviewThreadLine(thread)).toBe(6);
    });

    it('should prefer current line over initial fallback', () => {
        const thread = { lineNumber: 4, initialLineNumber: 9 } as any;
        expect(getReviewThreadLine(thread)).toBe(3);
    });

    it('should build a problem-statement draft location', () => {
        expect(buildProblemStatementDraftLocation(12)).toEqual({
            targetType: CommentThreadLocationType.PROBLEM_STATEMENT,
            lineNumber: 12,
        });
    });

    it('should detect problem-statement threads', () => {
        expect(isProblemStatementThread({ targetType: CommentThreadLocationType.PROBLEM_STATEMENT } as any)).toBe(true);
        expect(isProblemStatementThread({ targetType: CommentThreadLocationType.TEMPLATE_REPO } as any)).toBe(false);
    });
});

describe('matchesSelectedRepository', () => {
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

describe('buildRepositoryDraftLocation', () => {
    it('should return undefined when repository type is missing', () => {
        expect(buildRepositoryDraftLocation(undefined, 'src/File.java', 3)).toBeUndefined();
    });

    it('should build template repository draft location', () => {
        expect(buildRepositoryDraftLocation(RepositoryType.TEMPLATE, 'src/File.java', 3)).toEqual({
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            filePath: 'src/File.java',
            lineNumber: 3,
            auxiliaryRepositoryId: undefined,
        });
    });

    it('should include auxiliary repository id for auxiliary repositories', () => {
        expect(buildRepositoryDraftLocation(RepositoryType.AUXILIARY, 'src/File.java', 8, 11)).toEqual({
            targetType: CommentThreadLocationType.AUXILIARY_REPO,
            filePath: 'src/File.java',
            lineNumber: 8,
            auxiliaryRepositoryId: 11,
        });
    });
});
