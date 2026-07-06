import {
    adaptFinding,
    adaptFindingTagSeverity,
    firstConsistencyIssueContent,
    getFirstCommentByCreatedDateThenId,
    isReviewCommentsSupportedRepository,
    mapRepositoryToThreadLocationType,
    matchesSelectedRepository,
    reviewRepositoryLabel,
    selectedThreadsFindings,
    sortCommentsByCreatedDateThenId,
    threadLocationLabel,
} from 'app/exercise/review/review-comment-utils';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContentType } from 'app/exercise/shared/entities/review/comment-content.model';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, describe, expect, it, vi } from 'vitest';

// Echoes the translation key back so label assertions are deterministic and readable.
const translate = { instant: (key: string) => key } as unknown as TranslateService;

function consistencyComment(overrides: Partial<{ severity: ConsistencyIssue.SeverityEnum; category: ConsistencyIssue.CategoryEnum; text: string; suggestedFix: any }> = {}) {
    return {
        id: 1,
        createdDate: '2024-01-01T00:00:00Z',
        type: CommentType.CONSISTENCY_CHECK,
        content: {
            contentType: CommentContentType.CONSISTENCY_CHECK,
            severity: overrides.severity ?? ConsistencyIssue.SeverityEnum.High,
            category: overrides.category ?? ConsistencyIssue.CategoryEnum.MethodParameterMismatch,
            text: overrides.text ?? 'mismatch',
            suggestedFix: overrides.suggestedFix,
        },
    } as any;
}

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

describe('reviewRepositoryLabel', () => {
    it.each([
        [CommentThreadLocationType.PROBLEM_STATEMENT, 'artemisApp.review.relatedLocationRepository.problemStatement'],
        [CommentThreadLocationType.TEMPLATE_REPO, 'artemisApp.review.relatedLocationRepository.template'],
        [CommentThreadLocationType.SOLUTION_REPO, 'artemisApp.review.relatedLocationRepository.solution'],
        [CommentThreadLocationType.TEST_REPO, 'artemisApp.review.relatedLocationRepository.tests'],
        [CommentThreadLocationType.AUXILIARY_REPO, 'artemisApp.review.relatedLocationRepository.auxiliary'],
    ])('should resolve the label for %s', (targetType, key) => {
        expect(reviewRepositoryLabel(targetType, translate)).toBe(key);
    });

    it('should fall back to the generic repository label for an unknown target type', () => {
        expect(reviewRepositoryLabel('SOMETHING_ELSE' as CommentThreadLocationType, translate)).toBe('artemisApp.review.relatedLocationRepository.repository');
    });
});

describe('threadLocationLabel', () => {
    it('should label a problem-statement thread as repository:line without a file path', () => {
        const thread = { targetType: CommentThreadLocationType.PROBLEM_STATEMENT, lineNumber: 7 } as any;
        expect(threadLocationLabel(thread, translate)).toBe('artemisApp.review.relatedLocationRepository.problemStatement:7');
    });

    it('should label a repository thread as repository: file:line', () => {
        const thread = { targetType: CommentThreadLocationType.SOLUTION_REPO, filePath: 'src/Foo.java', lineNumber: 12 } as any;
        expect(threadLocationLabel(thread, translate)).toBe('artemisApp.review.relatedLocationRepository.solution: src/Foo.java:12');
    });

    it('should fall back to initialLineNumber and initialFilePath', () => {
        const thread = { targetType: CommentThreadLocationType.TEST_REPO, initialFilePath: 'test/Bar.java', initialLineNumber: 3 } as any;
        expect(threadLocationLabel(thread, translate)).toBe('artemisApp.review.relatedLocationRepository.tests: test/Bar.java:3');
    });

    it('should return undefined when the line number is below 1', () => {
        const thread = { targetType: CommentThreadLocationType.SOLUTION_REPO, filePath: 'src/Foo.java', lineNumber: 0, initialLineNumber: 0 } as any;
        expect(threadLocationLabel(thread, translate)).toBeUndefined();
    });

    it('should return undefined when there is no concrete line', () => {
        const thread = { targetType: CommentThreadLocationType.SOLUTION_REPO, filePath: 'src/Foo.java' } as any;
        expect(threadLocationLabel(thread, translate)).toBeUndefined();
    });

    it('should return undefined for a repository thread with a line but no file path', () => {
        const thread = { targetType: CommentThreadLocationType.SOLUTION_REPO, lineNumber: 9 } as any;
        expect(threadLocationLabel(thread, translate)).toBeUndefined();
    });
});

describe('firstConsistencyIssueContent', () => {
    it('should return the consistency content when the first comment is a consistency check', () => {
        const thread = { comments: [consistencyComment({ text: 'boom' })] } as any;
        expect(firstConsistencyIssueContent(thread)?.text).toBe('boom');
    });

    it('should return undefined when the first comment is a user comment', () => {
        const userComment = { id: 1, createdDate: '2024-01-01T00:00:00Z', type: CommentType.USER, content: { contentType: CommentContentType.USER, text: 'hi' } } as any;
        const thread = { comments: [userComment] } as any;
        expect(firstConsistencyIssueContent(thread)).toBeUndefined();
    });

    it('should return undefined when the thread has no comments', () => {
        expect(firstConsistencyIssueContent({ comments: [] } as any)).toBeUndefined();
    });

    it('should inspect the chronologically first comment, not array order', () => {
        const later = consistencyComment({ text: 'later' });
        later.createdDate = '2024-01-02T00:00:00Z';
        const earlierUser = { id: 2, createdDate: '2024-01-01T00:00:00Z', type: CommentType.USER, content: { contentType: CommentContentType.USER, text: 'hi' } } as any;
        const thread = { comments: [later, earlierUser] } as any;
        // The earlier (user) comment wins chronologically, so this is not a consistency finding.
        expect(firstConsistencyIssueContent(thread)).toBeUndefined();
    });
});

describe('adaptFinding', () => {
    it('should project the consistency content into a structured finding', () => {
        const issue = {
            contentType: CommentContentType.CONSISTENCY_CHECK,
            severity: ConsistencyIssue.SeverityEnum.Medium,
            category: ConsistencyIssue.CategoryEnum.AttributeTypeMismatch,
            text: 'wrong type',
            suggestedFix: { startLine: 1, endLine: 2, applied: false },
        } as any;

        expect(adaptFinding(issue, 'loc:3')).toEqual({
            category: ConsistencyIssue.CategoryEnum.AttributeTypeMismatch,
            severity: ConsistencyIssue.SeverityEnum.Medium,
            tagSeverity: 'warn',
            locationLabel: 'loc:3',
            description: 'wrong type',
            suggestedFix: { startLine: 1, endLine: 2, applied: false },
        });
    });

    it('should coerce a null suggestedFix to undefined', () => {
        const issue = { severity: ConsistencyIssue.SeverityEnum.Low, category: ConsistencyIssue.CategoryEnum.VisibilityMismatch, text: 't', suggestedFix: null } as any;
        expect(adaptFinding(issue, undefined).suggestedFix).toBeUndefined();
    });
});

describe('adaptFindingTagSeverity', () => {
    it('should map each severity to its PrimeNG tag severity', () => {
        expect(adaptFindingTagSeverity(ConsistencyIssue.SeverityEnum.High)).toBe('danger');
        expect(adaptFindingTagSeverity(ConsistencyIssue.SeverityEnum.Medium)).toBe('warn');
        expect(adaptFindingTagSeverity(ConsistencyIssue.SeverityEnum.Low)).toBe('info');
    });
});

describe('selectedThreadsFindings', () => {
    it('should derive findings only from consistency threads, preserving input thread order', () => {
        const consistencyThreadB = {
            targetType: CommentThreadLocationType.SOLUTION_REPO,
            filePath: 'src/B.java',
            lineNumber: 9,
            comments: [consistencyComment({ text: 'second' })],
        } as any;
        const consistencyThreadA = {
            targetType: CommentThreadLocationType.SOLUTION_REPO,
            filePath: 'src/A.java',
            lineNumber: 5,
            comments: [consistencyComment({ text: 'first' })],
        } as any;
        const plainThread = {
            targetType: CommentThreadLocationType.SOLUTION_REPO,
            comments: [{ id: 9, createdDate: '2024-01-01T00:00:00Z', type: CommentType.USER, content: { contentType: CommentContentType.USER, text: 'note' } }],
        } as any;

        // The plain (non-consistency) thread is dropped; the two consistency threads keep the order they were passed in (B before A).
        const findings = selectedThreadsFindings([consistencyThreadB, plainThread, consistencyThreadA], translate);

        expect(findings.map((f) => f.description)).toEqual(['second', 'first']);
        expect(findings.map((f) => f.locationLabel)).toEqual([
            'artemisApp.review.relatedLocationRepository.solution: src/B.java:9',
            'artemisApp.review.relatedLocationRepository.solution: src/A.java:5',
        ]);
    });

    it('should return an empty array when no thread is a consistency finding', () => {
        const plainThread = {
            targetType: CommentThreadLocationType.SOLUTION_REPO,
            comments: [{ id: 9, createdDate: '2024-01-01T00:00:00Z', type: CommentType.USER, content: { contentType: CommentContentType.USER, text: 'note' } }],
        } as any;
        expect(selectedThreadsFindings([plainThread], translate)).toEqual([]);
    });
});
