import {
    adaptFindingText,
    combineAdaptFeedback,
    firstConsistencyIssueContent,
    getFirstCommentByCreatedDateThenId,
    isReviewCommentsSupportedRepository,
    mapRepositoryToThreadLocationType,
    matchesSelectedRepository,
    selectedThreadsFindingsText,
    sortCommentsByCreatedDateThenId,
    threadLocationLabel,
} from 'app/exercise/review/review-comment-utils';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { CommentContentType } from 'app/exercise/shared/entities/review/comment-content.model';
import { CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { afterEach, describe, expect, it, vi } from 'vitest';

/** A translate stub that echoes its key, so assertions read structurally. */
const translate = { instant: (key: string) => key } as any;

/** Builds a thread whose first comment is a consistency-check finding. */
function consistencyThread(
    overrides: { id?: number; category?: string; severity?: string; text?: string; targetType?: CommentThreadLocationType; filePath?: string; lineNumber?: number } = {},
) {
    return {
        id: overrides.id ?? 1,
        targetType: overrides.targetType ?? CommentThreadLocationType.SOLUTION_REPO,
        filePath: overrides.filePath,
        lineNumber: overrides.lineNumber,
        comments: [
            {
                id: 1,
                type: CommentType.CONSISTENCY_CHECK,
                createdDate: '2024-01-01T00:00:00Z',
                content: {
                    contentType: CommentContentType.CONSISTENCY_CHECK,
                    category: overrides.category ?? 'CAT',
                    severity: overrides.severity ?? 'HIGH',
                    text: overrides.text ?? 'finding text',
                },
            },
        ],
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

describe('adapt feedback assembly', () => {
    it('firstConsistencyIssueContent returns the content for a consistency thread and undefined otherwise', () => {
        expect(firstConsistencyIssueContent(consistencyThread({ text: 'x' }))?.text).toBe('x');
        const userThread = {
            comments: [{ id: 1, type: CommentType.USER, createdDate: '2024-01-01T00:00:00Z', content: { contentType: CommentContentType.USER, text: 'hi' } }],
        } as any;
        expect(firstConsistencyIssueContent(userThread)).toBeUndefined();
        expect(firstConsistencyIssueContent({ comments: [] } as any)).toBeUndefined();
    });

    it('threadLocationLabel includes the repository, file and line; undefined without a line', () => {
        const label = threadLocationLabel(consistencyThread({ targetType: CommentThreadLocationType.SOLUTION_REPO, filePath: 'src/A.java', lineNumber: 12 }), translate);
        expect(label).toBe('artemisApp.review.relatedLocationRepository.solution: src/A.java:12');
        // No line at all -> undefined; and a line without a file path is equally insufficient for a concrete location (both guard clauses must hold).
        expect(threadLocationLabel(consistencyThread({ lineNumber: undefined }), translate)).toBeUndefined();
        expect(threadLocationLabel(consistencyThread({ lineNumber: 5, filePath: undefined }), translate)).toBeUndefined();
    });

    it('adaptFindingText assembles "category (severity) — location\\ntext" exactly', () => {
        const text = adaptFindingText({ category: 'C', severity: 'HIGH', text: 'the issue' } as any, 'Solution: A.java:3', translate);
        // Pin the exact format (field order, the em-dash separator, the header\ntext join) so a reorder or separator change fails.
        expect(text).toBe('artemisApp.hyperion.consistencyCheck.category.C (artemisApp.review.consistencySeverity.HIGH) — Solution: A.java:3\nthe issue');
    });

    it('adaptFindingText omits the location segment when there is none', () => {
        const text = adaptFindingText({ category: 'C', severity: 'LOW', text: 'no location' } as any, undefined, translate);
        expect(text).toBe('artemisApp.hyperion.consistencyCheck.category.C (artemisApp.review.consistencySeverity.LOW)\nno location');
    });

    it('combineAdaptFeedback appends instructions only when provided', () => {
        const withInstructions = combineAdaptFeedback('FINDINGS', 'please simplify', translate);
        expect(withInstructions).toContain('artemisApp.review.adaptExercise.feedbackLabel');
        expect(withInstructions).toContain('FINDINGS');
        expect(withInstructions).toContain('artemisApp.review.adaptExercise.instructionsLabel');
        expect(withInstructions).toContain('please simplify');

        const withoutInstructions = combineAdaptFeedback('FINDINGS', '   ', translate);
        expect(withoutInstructions).toContain('FINDINGS');
        expect(withoutInstructions).not.toContain('artemisApp.review.adaptExercise.instructionsLabel');
    });

    it('selectedThreadsFindingsText numbers multiple findings, omits the number for one, and drops non-consistency threads', () => {
        const userThread = {
            comments: [{ id: 9, type: CommentType.USER, createdDate: '2024-01-01T00:00:00Z', content: { contentType: CommentContentType.USER, text: 'hi' } }],
        } as any;
        const single = selectedThreadsFindingsText([consistencyThread({ text: 'only one' }), userThread], translate);
        expect(single).toContain('only one');
        expect(single).not.toMatch(/^1\./);

        const multi = selectedThreadsFindingsText([consistencyThread({ id: 1, text: 'first' }), consistencyThread({ id: 2, text: 'second' })], translate);
        // Pin the exact joined shape: "1. <finding>\n\n2. <finding>", each finding being "category (severity)\ntext" (no location, as these threads have no line).
        expect(multi).toBe(
            '1. artemisApp.hyperion.consistencyCheck.category.CAT (artemisApp.review.consistencySeverity.HIGH)\nfirst' +
                '\n\n2. artemisApp.hyperion.consistencyCheck.category.CAT (artemisApp.review.consistencySeverity.HIGH)\nsecond',
        );

        expect(selectedThreadsFindingsText([userThread], translate)).toBe('');
    });
});
