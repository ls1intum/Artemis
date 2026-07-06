import { CommentThread, CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { Comment, CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContent, CommentContentType, ConsistencyIssueCommentContent, InlineCodeChange } from 'app/exercise/shared/entities/review/comment-content.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { TranslateService } from '@ngx-translate/core';

/** Sorts comments by creation timestamp and then by id for deterministic ordering. */
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

/** Returns the first comment according to chronological ordering by creation timestamp and id. */
export function getFirstCommentByCreatedDateThenId(comments: Comment[] | undefined): Comment | undefined {
    return sortCommentsByCreatedDateThenId(comments)[0];
}

/** Checks whether a thread belongs to the currently selected repository. */
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

/** Maps a repository type to the corresponding thread target type. */
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

/** Checks whether review comments are supported for the selected repository. */
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
 * Returns the consistency-issue content of a thread's first (chronological) comment, or {@code undefined} if that comment is not a consistency-check finding. Used to decide whether
 * a thread can be turned into Artemis Intelligence adapt feedback.
 */
export function consistencyIssueContentOf(firstComment: Comment | undefined): ConsistencyIssueCommentContent | undefined {
    if (!firstComment || firstComment.type !== CommentType.CONSISTENCY_CHECK) {
        return undefined;
    }
    const content = firstComment.content as CommentContent | undefined;
    if (!content || content.contentType !== CommentContentType.CONSISTENCY_CHECK) {
        return undefined;
    }
    return content;
}

export function firstConsistencyIssueContent(thread: CommentThread): ConsistencyIssueCommentContent | undefined {
    return consistencyIssueContentOf(getFirstCommentByCreatedDateThenId(thread.comments));
}

/** Maps a thread location type to its human-readable repository label. */
export function reviewRepositoryLabel(targetType: CommentThreadLocationType, translate: TranslateService): string {
    switch (targetType) {
        case CommentThreadLocationType.PROBLEM_STATEMENT:
            return translate.instant('artemisApp.review.relatedLocationRepository.problemStatement');
        case CommentThreadLocationType.TEMPLATE_REPO:
            return translate.instant('artemisApp.review.relatedLocationRepository.template');
        case CommentThreadLocationType.SOLUTION_REPO:
            return translate.instant('artemisApp.review.relatedLocationRepository.solution');
        case CommentThreadLocationType.TEST_REPO:
            return translate.instant('artemisApp.review.relatedLocationRepository.tests');
        case CommentThreadLocationType.AUXILIARY_REPO:
            return translate.instant('artemisApp.review.relatedLocationRepository.auxiliary');
        default:
            return translate.instant('artemisApp.review.relatedLocationRepository.repository');
    }
}

/** Builds a short location label ({@code Repository: file:line}) for a thread, or {@code undefined} when it has no concrete line. */
export function threadLocationLabel(thread: CommentThread, translate: TranslateService): string | undefined {
    const lineNumber = thread.lineNumber ?? thread.initialLineNumber;
    if (!lineNumber || lineNumber < 1) {
        return undefined;
    }
    const repositoryLabel = reviewRepositoryLabel(thread.targetType, translate);
    if (thread.targetType === CommentThreadLocationType.PROBLEM_STATEMENT) {
        return `${repositoryLabel}:${lineNumber}`;
    }
    const filePath = thread.filePath ?? thread.initialFilePath;
    if (!filePath) {
        return undefined;
    }
    return `${repositoryLabel}: ${filePath}:${lineNumber}`;
}

/**
 * A structured consistency finding for the adapt dialog's read-only display (severity tag, category, location, description, suggested fix). The dialog renders these as cards; the
 * agent prompt itself is assembled server-side from the selected thread ids, so this type never leaves the client.
 */
export type AdaptFindingTagSeverity = 'danger' | 'warn' | 'info';

/** Maps a finding severity to its PrimeNG tag severity. Called once at build time so the template binds a plain field, not a per-change-detection method. */
export function adaptFindingTagSeverity(severity: ConsistencyIssueCommentContent['severity']): AdaptFindingTagSeverity {
    switch (severity) {
        case ConsistencyIssue.SeverityEnum.High:
            return 'danger';
        case ConsistencyIssue.SeverityEnum.Medium:
            return 'warn';
        default:
            return 'info';
    }
}

export interface AdaptFinding {
    category: ConsistencyIssueCommentContent['category'];
    severity: ConsistencyIssueCommentContent['severity'];
    /** The PrimeNG tag severity for the coloured severity tag, precomputed so the template binds a field rather than a per-change-detection method. */
    tagSeverity: AdaptFindingTagSeverity;
    /** A short {@code Repository: file:line} label, absent when the thread has no concrete line. */
    locationLabel?: string;
    /** The finding's description text. */
    description: string;
    /** The optional concrete code change the check suggests. */
    suggestedFix?: InlineCodeChange;
}

/** Builds the structured {@link AdaptFinding} shown in the adapt dialog for a single consistency-issue content. */
export function adaptFinding(issueContent: ConsistencyIssueCommentContent, locationLabel: string | undefined): AdaptFinding {
    return {
        category: issueContent.category,
        severity: issueContent.severity,
        tagSeverity: adaptFindingTagSeverity(issueContent.severity),
        locationLabel,
        description: issueContent.text,
        suggestedFix: issueContent.suggestedFix ?? undefined,
    };
}

/**
 * The structured findings for a set of threads (only consistency-issue threads contribute), in thread order. Used for the read-only cards in the adapt dialog.
 */
export function selectedThreadsFindings(threads: CommentThread[], translate: TranslateService): AdaptFinding[] {
    return threads
        .map((thread) => {
            const issue = firstConsistencyIssueContent(thread);
            return issue ? adaptFinding(issue, threadLocationLabel(thread, translate)) : undefined;
        })
        .filter((finding): finding is AdaptFinding => !!finding);
}
