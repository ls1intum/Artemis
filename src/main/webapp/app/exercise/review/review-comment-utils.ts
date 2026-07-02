import { TranslateService } from '@ngx-translate/core';
import { CommentThread, CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { Comment, CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContent, CommentContentType, ConsistencyIssueCommentContent, InlineCodeChange } from 'app/exercise/shared/entities/review/comment-content.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

/**
 * Responsive width breakpoints for the adapt-exercise dialog's PrimeNG DynamicDialog. The base width is a narrow 40vw on wide screens, but a fixed 40vw is unreadably narrow on
 * smaller viewports, so the dialog widens toward near-full-width as the viewport shrinks. Shared by every open() call site so the dialog sizes consistently wherever it is launched.
 */
export const ADAPT_DIALOG_BREAKPOINTS: Record<string, string> = { '1280px': '55vw', '960px': '75vw', '640px': '94vw' };

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

/**
 * Returns the consistency-issue content of a thread's first (chronological) comment, or {@code undefined} if that comment is not a consistency-check finding. Used to decide whether
 * a thread can be turned into Artemis Intelligence adapt feedback.
 *
 * @param thread The comment thread to inspect.
 */
export function firstConsistencyIssueContent(thread: CommentThread): ConsistencyIssueCommentContent | undefined {
    const firstComment = getFirstCommentByCreatedDateThenId(thread.comments);
    if (!firstComment || firstComment.type !== CommentType.CONSISTENCY_CHECK) {
        return undefined;
    }
    const content = firstComment.content as CommentContent | undefined;
    if (!content || content.contentType !== CommentContentType.CONSISTENCY_CHECK) {
        return undefined;
    }
    return content;
}

/**
 * Maps a thread location type to its human-readable repository label.
 */
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

/**
 * Builds a short location label ({@code Repository: file:line}) for a thread, or {@code undefined} when it has no concrete line.
 */
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
 * The human-readable description of one finding (category, severity, location, text) shown in the adapt dialog and embedded in the feedback prompt.
 */
export function adaptFindingText(issueContent: ConsistencyIssueCommentContent, locationLabel: string | undefined, translate: TranslateService): string {
    const category = translate.instant('artemisApp.hyperion.consistencyCheck.category.' + issueContent.category);
    const severity = translate.instant('artemisApp.review.consistencySeverity.' + issueContent.severity);
    const header = locationLabel ? `${category} (${severity}) — ${locationLabel}` : `${category} (${severity})`;
    return `${header}\n${issueContent.text}`.trim();
}

/**
 * A structured consistency finding for the adapt dialog's display (severity tag, category, location, description, suggested fix), as opposed to the flattened {@link adaptFindingText}
 * string that goes into the agent prompt. The dialog renders these as cards; the prompt path keeps using the text builders so the agent's input never changes.
 */
export interface AdaptFinding {
    category: ConsistencyIssueCommentContent['category'];
    severity: ConsistencyIssueCommentContent['severity'];
    /** A short {@code Repository: file:line} label, absent when the thread has no concrete line. */
    locationLabel?: string;
    /** The finding's description text. */
    description: string;
    /** The optional concrete code change the check suggests. */
    suggestedFix?: InlineCodeChange | null;
}

/** Builds the structured {@link AdaptFinding} (for display) that mirrors {@link adaptFindingText} (for the prompt). */
export function adaptFinding(issueContent: ConsistencyIssueCommentContent, locationLabel: string | undefined): AdaptFinding {
    return { category: issueContent.category, severity: issueContent.severity, locationLabel, description: issueContent.text, suggestedFix: issueContent.suggestedFix };
}

/** The structured findings for a set of threads (only consistency-issue threads contribute); the array mirror of {@link selectedThreadsFindingsText}. */
export function selectedThreadsFindings(threads: CommentThread[], translate: TranslateService): AdaptFinding[] {
    return threads
        .map((thread) => {
            const issue = firstConsistencyIssueContent(thread);
            return issue ? adaptFinding(issue, threadLocationLabel(thread, translate)) : undefined;
        })
        .filter((finding): finding is AdaptFinding => !!finding);
}

/**
 * The combined, numbered findings text for a set of threads (only consistency-issue threads contribute); empty string when none qualify. A single finding is rendered without a
 * leading number.
 */
export function selectedThreadsFindingsText(threads: CommentThread[], translate: TranslateService): string {
    const findings = threads
        .map((thread) => {
            const issue = firstConsistencyIssueContent(thread);
            return issue ? adaptFindingText(issue, threadLocationLabel(thread, translate), translate) : undefined;
        })
        .filter((text): text is string => !!text);
    if (findings.length <= 1) {
        return findings[0] ?? '';
    }
    return findings.map((text, index) => `${index + 1}. ${text}`).join('\n\n');
}

/**
 * Assembles the adapt feedback prompt sent to Artemis Intelligence: the finding(s) to address followed by any optional instructor instructions.
 */
export function combineAdaptFeedback(findingsText: string, instructions: string | undefined, translate: TranslateService): string {
    const findingSection = `${translate.instant('artemisApp.review.adaptExercise.feedbackLabel')}\n${findingsText}`;
    const trimmedInstructions = instructions?.trim();
    if (!trimmedInstructions) {
        return findingSection.trim();
    }
    const instructionsSection = `${translate.instant('artemisApp.review.adaptExercise.instructionsLabel')}\n${trimmedInstructions}`;
    return `${findingSection}\n\n${instructionsSection}`.trim();
}
