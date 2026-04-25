import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    OnDestroy,
    OnInit,
    ViewEncapsulation,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
    viewChild,
    viewChildren,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ButtonDirective } from 'primeng/button';
import { ConfirmationService, MenuItem } from 'primeng/api';
import { Menu, MenuModule } from 'primeng/menu';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowUpRightFromSquare, faChevronDown, faEllipsisVertical, faPen, faTrash, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { CommentThread, CommentThreadLocationType, ReviewThreadLocation } from 'app/exercise/shared/entities/review/comment-thread.model';
import { Comment, CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContent, CommentContentType, ConsistencyIssueCommentContent, InlineCodeChange } from 'app/exercise/shared/entities/review/comment-content.model';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { takeUntil } from 'rxjs/operators';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { sortCommentsByCreatedDateThenId } from 'app/exercise/review/review-comment-utils';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/diff-editor/monaco-diff-editor.component';
import { CUSTOM_MARKDOWN_LANGUAGE_ID } from 'app/shared/monaco-editor/model/languages/monaco-custom-markdown.language';

interface RelatedThreadLocation {
    threadId: number;
    locationLabel: string;
}

@Component({
    selector: 'jhi-review-comment-thread-widget',
    templateUrl: './review-comment-thread-widget.component.html',
    styleUrls: ['./review-comment-thread-widget.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    // Monaco view zones render outside Angular's host tree, so styles must stay global.
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    imports: [FormsModule, ButtonDirective, MenuModule, ConfirmDialogModule, ArtemisTranslatePipe, ArtemisDatePipe, FaIconComponent, MonacoDiffEditorComponent],
    providers: [ConfirmationService],
})
export class ReviewCommentThreadWidgetComponent implements OnInit, OnDestroy {
    readonly thread = input.required<CommentThread>();
    readonly initialCollapsed = input<boolean>(false);
    readonly showLocationWarning = input<boolean>(false);
    readonly showFeedbackAction = input<boolean>(false);

    readonly onToggleCollapse = output<boolean>();
    readonly onNavigateToLocation = output<ReviewThreadLocation>();
    readonly onApplyInlineFix = output<InlineCodeChange>();

    readonly replyText = signal('');
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faEllipsisVertical = faEllipsisVertical;
    protected readonly faPen = faPen;
    protected readonly faTrash = faTrash;
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;
    protected readonly faChevronDown = faChevronDown;
    readonly showThreadBody = signal(true);
    readonly languageVersion = signal(0);
    readonly editingCommentId = signal<number | undefined>(undefined);
    readonly editingCommentType = signal<CommentType | undefined>(undefined);
    readonly editText = signal('');
    userCommentMenuItems: MenuItem[] = [];
    nonUserCommentMenuItems: MenuItem[] = [];
    resolveGroupMenuItems: MenuItem[] = [];
    readonly commentMenus = viewChildren<Menu>('commentMenu');
    readonly resolveGroupMenu = viewChild<Menu>('resolveGroupMenu');
    readonly suggestedInlineFixDiffEditor = viewChild(MonacoDiffEditorComponent);

    private readonly destroyed$ = new Subject<void>();
    private readonly translateService = inject(TranslateService);
    private readonly changeDetectorRef = inject(ChangeDetectorRef);
    private readonly reviewCommentService = inject(ExerciseReviewCommentService);
    private readonly confirmationService = inject(ConfirmationService);
    readonly deleteCommentDialogKey = computed(() => `review-comment-delete-${this.thread().id}`);
    readonly orderedComments = computed(() => sortCommentsByCreatedDateThenId(this.thread().comments));
    readonly renderedComments = computed(() => {
        return this.orderedComments().map((comment) => ({
            comment,
            authorName: comment.authorName,
            isEdited: this.isEdited(comment),
            displayText: this.formatReviewCommentText(comment),
        }));
    });
    readonly isSelectedAsFeedback = computed(() => this.reviewCommentService.isThreadSelectedAsFeedback(this.thread().id));
    readonly firstComment = computed(() => this.orderedComments()[0]);
    readonly firstConsistencyIssueContent = computed<ConsistencyIssueCommentContent | undefined>(() => {
        const firstComment = this.firstComment();
        if (!firstComment || !this.isConsistencyCheckComment(firstComment)) {
            return undefined;
        }
        const content = firstComment.content as CommentContent | undefined;
        if (!content || content.contentType !== CommentContentType.CONSISTENCY_CHECK) {
            return undefined;
        }
        return content;
    });
    readonly isConsistencyIssueThread = computed(() => this.firstConsistencyIssueContent() !== undefined);
    readonly consistencySuggestedInlineFix = computed<InlineCodeChange | undefined>(() => this.getValidSuggestedInlineFix(this.firstConsistencyIssueContent()?.suggestedFix));
    readonly showInlineFixOutdatedWarning = signal(false);
    readonly canResolveGroup = computed(() => {
        const groupId = this.thread().groupId;
        if (groupId === undefined) {
            return false;
        }
        return this.reviewCommentService.threads().some((thread) => thread.groupId === groupId && !thread.resolved);
    });
    readonly canUnresolveGroup = computed(() => {
        const groupId = this.thread().groupId;
        if (groupId === undefined) {
            return false;
        }
        return this.reviewCommentService.threads().some((thread) => thread.groupId === groupId && thread.resolved);
    });
    readonly relatedGroupLocations = computed<RelatedThreadLocation[]>(() => {
        // Recompute related location labels when language changes because repository labels are translated.
        this.languageVersion();
        const currentThread = this.thread();
        const groupId = currentThread.groupId;

        if (!this.isConsistencyIssueThread() || groupId === undefined) {
            return [];
        }

        const distinctLocations = new Map<string, RelatedThreadLocation>();
        for (const groupedThread of this.reviewCommentService.threads()) {
            if (groupedThread.groupId !== groupId || groupedThread.id === currentThread.id) {
                continue;
            }

            const locationLabel = this.getThreadLocationLabel(groupedThread);
            if (!locationLabel) {
                continue;
            }

            distinctLocations.set(locationLabel, { threadId: groupedThread.id, locationLabel });
        }

        return Array.from(distinctLocations.values()).sort((a, b) => a.locationLabel.localeCompare(b.locationLabel));
    });

    constructor() {
        effect(() => {
            const inlineFix = this.consistencySuggestedInlineFix();
            const diffEditor = this.suggestedInlineFixDiffEditor();
            const thread = this.thread();
            if (!inlineFix || !diffEditor) {
                return;
            }

            diffEditor.setFileContents(
                inlineFix.expectedCode,
                inlineFix.replacementCode,
                this.getInlineFixDiffFileName(thread),
                this.getInlineFixDiffFileName(thread),
                this.getInlineFixDiffLanguageId(thread),
            );
        });

        effect(() => {
            this.canResolveGroup();
            this.canUnresolveGroup();
            this.languageVersion();
            this.updateMenuItems();
        });
    }

    /**
     * Deletes the given comment via the review comment service.
     *
     * @param commentId The id of the comment to delete.
     */
    deleteComment(commentId: number): void {
        this.confirmationService.confirm({
            key: this.deleteCommentDialogKey(),
            header: this.translateService.instant('artemisApp.review.deleteCommentConfirmTitle'),
            message: this.translateService.instant('artemisApp.review.deleteCommentConfirmText'),
            acceptButtonStyleClass: 'p-button-danger',
            accept: () => this.reviewCommentService.deleteCommentInContext(commentId),
        });
    }

    /**
     * Starts editing a comment and pre-fills the editor with formatted text.
     *
     * @param comment The comment to edit.
     */
    startEditing(comment: Comment): void {
        if (!this.isUserComment(comment)) {
            return;
        }
        this.editingCommentId.set(comment.id);
        this.editingCommentType.set(comment.type);
        this.editText.set(this.formatReviewCommentText(comment));
    }

    /**
     * Cancels the current edit and clears the editor state.
     */
    cancelEditing(): void {
        this.editingCommentId.set(undefined);
        this.editingCommentType.set(undefined);
        this.editText.set('');
    }

    /**
     * Saves the edited comment text when non-empty.
     */
    saveEditing(): void {
        const id = this.editingCommentId();
        const trimmed = this.editText().trim();
        if (id === undefined || !trimmed || this.editingCommentType() !== CommentType.USER) {
            return;
        }
        this.reviewCommentService.updateCommentInContext(id, { contentType: CommentContentType.USER, text: trimmed }, () => this.cancelEditing());
    }

    /**
     * Creates a reply with trimmed text and clears the reply field.
     */
    submitReply(): void {
        const trimmed = this.replyText().trim();
        if (!trimmed) {
            return;
        }
        this.reviewCommentService.createReplyInContext(this.thread().id, { contentType: CommentContentType.USER, text: trimmed }, () => {
            this.replyText.set('');
        });
    }

    /**
     * Toggles the resolved state and collapses the thread when resolved.
     */
    toggleResolved(): void {
        const thread = this.thread();
        const nextResolved = !thread.resolved;
        this.reviewCommentService.toggleResolvedInContext(thread.id, nextResolved);
        if (nextResolved) {
            this.showThreadBody.set(false);
            this.onToggleCollapse.emit(true);
        }
    }

    /**
     * Toggles whether the current thread should be included as feedback in the next Hyperion generation request.
     */
    toggleFeedbackSelection(): void {
        if (!this.showFeedbackAction()) {
            return;
        }
        this.reviewCommentService.toggleThreadFeedbackSelection(this.thread().id);
    }

    /**
     * Resolves all threads in the current thread group.
     */
    resolveGroup(): void {
        const groupId = this.thread().groupId;
        if (groupId === undefined) {
            return;
        }
        this.reviewCommentService.toggleGroupResolvedInContext(groupId, true);
        this.showThreadBody.set(false);
        this.onToggleCollapse.emit(true);
    }

    /**
     * Reopens all threads in the current thread group.
     */
    unresolveGroup(): void {
        const groupId = this.thread().groupId;
        if (groupId === undefined) {
            return;
        }
        this.reviewCommentService.toggleGroupResolvedInContext(groupId, false);
    }

    /**
     * Toggles the thread body and emits the collapsed state.
     */
    toggleThreadBody(): void {
        const nextVisibleState = !this.showThreadBody();
        this.showThreadBody.set(nextVisibleState);
        this.onToggleCollapse.emit(!nextVisibleState);
    }

    /**
     * Emits a request to apply the suggested inline fix in the active editor context.
     *
     * @param inlineFix The inline fix payload to apply.
     */
    applySuggestedInlineFix(inlineFix: InlineCodeChange): void {
        this.showInlineFixOutdatedWarning.set(false);
        this.onApplyInlineFix.emit(inlineFix);
    }

    /**
     * Sets whether the inline fix cannot be applied because the current editor code is out of date.
     *
     * @param showWarning Whether to show the warning next to the apply button.
     */
    setInlineFixOutdatedWarning(showWarning: boolean): void {
        this.showInlineFixOutdatedWarning.set(showWarning);
    }

    ngOnInit(): void {
        this.showThreadBody.set(!this.initialCollapsed());
        this.updateMenuItems();
        document.addEventListener('scroll', this.hideOpenMenus, true);
        this.translateService.onLangChange.pipe(takeUntil(this.destroyed$)).subscribe(() => {
            this.updateMenuItems();
            this.languageVersion.update((version) => version + 1);
            this.changeDetectorRef.detectChanges();
        });
    }

    ngOnDestroy(): void {
        document.removeEventListener('scroll', this.hideOpenMenus, true);
        this.destroyed$.next();
        this.destroyed$.complete();
    }

    /**
     * Checks whether a comment was edited by comparing timestamps.
     *
     * @param comment The comment to check.
     * @returns True if the comment has been edited.
     */
    isEdited(comment: Comment): boolean {
        if (!comment.lastModifiedDate || !comment.createdDate) {
            return false;
        }
        return comment.lastModifiedDate !== comment.createdDate;
    }

    /**
     * Formats a comment for display, handling user and consistency content types.
     *
     * @param comment The comment whose content should be formatted.
     * @returns The formatted comment text.
     */
    formatReviewCommentText(comment: Comment): string {
        const content = comment.content as CommentContent | undefined;
        if (!content) {
            return '';
        }
        if ('contentType' in content && content.contentType === CommentContentType.CONSISTENCY_CHECK) {
            return content.text ?? '';
        }
        return content.text ?? '';
    }

    /**
     * Checks if the given comment is currently in edit mode.
     *
     * @param comment The comment to check.
     * @returns True if the comment is currently being edited.
     */
    isEditing(comment: Comment): boolean {
        return this.editingCommentId() === comment.id;
    }

    /**
     * Handles a selected comment-menu action.
     *
     * @param actionId The selected menu action identifier.
     * @param comment The affected comment.
     */
    handleCommentMenuAction(actionId: string | undefined, comment: Comment): void {
        if (actionId === 'edit') {
            this.startEditing(comment);
            return;
        }
        if (actionId === 'delete') {
            if (comment.id !== undefined) {
                this.deleteComment(comment.id);
            }
        }
    }

    /**
     * Handles a selected resolve-group menu action.
     *
     * @param actionId The selected menu action identifier.
     */
    handleResolveGroupMenuAction(actionId: string | undefined): void {
        if (actionId === 'resolve-group') {
            this.resolveGroup();
            return;
        }
        if (actionId === 'unresolve-group') {
            this.unresolveGroup();
        }
    }

    /**
     * Checks whether a comment was authored by a user.
     *
     * @param comment The comment to check.
     * @returns True if it is a user comment.
     */
    isUserComment(comment: Comment): boolean {
        return comment.type === CommentType.USER;
    }

    /**
     * Checks whether a comment is a consistency-check comment.
     *
     * @param comment The comment to check.
     * @returns True if it is a consistency-check comment.
     */
    isConsistencyCheckComment(comment: Comment): boolean {
        return comment.type === CommentType.CONSISTENCY_CHECK;
    }

    /**
     * Hides all currently open comment action menus in this thread widget.
     */
    hideAllCommentMenus(): void {
        for (const menu of this.commentMenus()) {
            menu.hide();
        }
        this.resolveGroupMenu()?.hide();
    }

    /**
     * Navigates to another thread location from the same consistency-check group.
     *
     * @param location The related location entry.
     */
    goToRelatedLocation(location: RelatedThreadLocation): void {
        const targetThread = this.reviewCommentService.threads().find((thread) => thread.id === location.threadId);
        if (!targetThread) {
            return;
        }

        this.onNavigateToLocation.emit({
            threadId: targetThread.id,
            targetType: targetThread.targetType,
            filePath: targetThread.filePath ?? targetThread.initialFilePath ?? undefined,
            lineNumber: targetThread.lineNumber ?? targetThread.initialLineNumber,
            auxiliaryRepositoryId: targetThread.auxiliaryRepositoryId,
        });
    }

    private updateMenuItems(): void {
        this.userCommentMenuItems = [
            { id: 'edit', label: this.translateService.instant('artemisApp.review.editComment') },
            { id: 'delete', label: this.translateService.instant('artemisApp.review.deleteComment') },
        ];
        this.nonUserCommentMenuItems = [{ id: 'delete', label: this.translateService.instant('artemisApp.review.deleteComment') }];
        const resolveGroupMenuItems: MenuItem[] = [];
        if (this.canResolveGroup()) {
            resolveGroupMenuItems.push({ id: 'resolve-group', label: this.translateService.instant('artemisApp.review.resolveThreadGroup') });
        }
        if (this.canUnresolveGroup()) {
            resolveGroupMenuItems.push({ id: 'unresolve-group', label: this.translateService.instant('artemisApp.review.unresolveThreadGroup') });
        }
        this.resolveGroupMenuItems = resolveGroupMenuItems;
    }

    private readonly hideOpenMenus = (): void => {
        this.hideAllCommentMenus();
    };

    private getThreadLocationLabel(thread: CommentThread): string | undefined {
        const lineNumber = thread.lineNumber ?? thread.initialLineNumber;
        if (!lineNumber || lineNumber < 1) {
            return undefined;
        }

        const repositoryLabel = this.getRepositoryLabel(thread.targetType);
        if (thread.targetType === CommentThreadLocationType.PROBLEM_STATEMENT) {
            return `${repositoryLabel}:${lineNumber}`;
        }

        const filePath = thread.filePath ?? thread.initialFilePath;
        if (!filePath) {
            return undefined;
        }

        return `${repositoryLabel}: ${filePath}:${lineNumber}`;
    }

    private getRepositoryLabel(targetType: CommentThreadLocationType): string {
        switch (targetType) {
            case CommentThreadLocationType.PROBLEM_STATEMENT:
                return this.translateService.instant('artemisApp.review.relatedLocationRepository.problemStatement');
            case CommentThreadLocationType.TEMPLATE_REPO:
                return this.translateService.instant('artemisApp.review.relatedLocationRepository.template');
            case CommentThreadLocationType.SOLUTION_REPO:
                return this.translateService.instant('artemisApp.review.relatedLocationRepository.solution');
            case CommentThreadLocationType.TEST_REPO:
                return this.translateService.instant('artemisApp.review.relatedLocationRepository.tests');
            case CommentThreadLocationType.AUXILIARY_REPO:
                return this.translateService.instant('artemisApp.review.relatedLocationRepository.auxiliary');
            default:
                return this.translateService.instant('artemisApp.review.relatedLocationRepository.repository');
        }
    }

    private getInlineFixDiffFileName(thread: CommentThread): string {
        if (thread.targetType === CommentThreadLocationType.PROBLEM_STATEMENT) {
            return 'problem_statement.md';
        }

        return thread.filePath ?? thread.initialFilePath ?? 'inline-fix.txt';
    }

    private getInlineFixDiffLanguageId(thread: CommentThread): string | undefined {
        if (thread.targetType === CommentThreadLocationType.PROBLEM_STATEMENT) {
            return CUSTOM_MARKDOWN_LANGUAGE_ID;
        }

        return undefined;
    }

    private getValidSuggestedInlineFix(inlineFix: InlineCodeChange | null | undefined): InlineCodeChange | undefined {
        if (!inlineFix || inlineFix.expectedCode == null || inlineFix.replacementCode == null || inlineFix.applied == null) {
            return undefined;
        }
        if (inlineFix.startLine == null || inlineFix.endLine == null || inlineFix.startLine < 1 || inlineFix.endLine < inlineFix.startLine) {
            return undefined;
        }
        return inlineFix;
    }
}
