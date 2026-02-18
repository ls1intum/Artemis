import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, ViewEncapsulation, computed, inject, input, output, signal, viewChildren } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ButtonDirective } from 'primeng/button';
import { MenuItem } from 'primeng/api';
import { Menu, MenuModule } from 'primeng/menu';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faEllipsisVertical, faPen, faTrash, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { CommentThread } from 'app/exercise/shared/entities/review/comment-thread.model';
import { Comment, CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContent, CommentContentType, ConsistencyIssueCommentContent } from 'app/exercise/shared/entities/review/comment-content.model';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { takeUntil } from 'rxjs/operators';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';

@Component({
    selector: 'jhi-review-comment-thread-widget',
    templateUrl: './review-comment-thread-widget.component.html',
    styleUrls: ['./review-comment-thread-widget.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    // Monaco view zones render outside Angular's host tree, so styles must stay global.
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    imports: [FormsModule, ButtonDirective, MenuModule, ArtemisTranslatePipe, ArtemisDatePipe, FaIconComponent],
})
export class ReviewCommentThreadWidgetComponent implements OnInit, OnDestroy {
    readonly thread = input.required<CommentThread>();
    readonly initialCollapsed = input<boolean>(false);
    readonly showLocationWarning = input<boolean>(false);

    readonly onToggleCollapse = output<boolean>();

    readonly replyText = signal('');
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faEllipsisVertical = faEllipsisVertical;
    protected readonly faPen = faPen;
    protected readonly faTrash = faTrash;
    readonly showThreadBody = signal(true);
    readonly editingCommentId = signal<number | undefined>(undefined);
    readonly editingCommentType = signal<CommentType | undefined>(undefined);
    readonly editText = signal('');
    userCommentMenuItems: MenuItem[] = [];
    nonUserCommentMenuItems: MenuItem[] = [];
    readonly commentMenus = viewChildren<Menu>('commentMenu');

    private readonly destroyed$ = new Subject<void>();
    private readonly translateService = inject(TranslateService);
    private readonly changeDetectorRef = inject(ChangeDetectorRef);
    private readonly reviewCommentService = inject(ExerciseReviewCommentService);
    readonly orderedComments = computed(() => {
        const comments = this.thread().comments ?? [];
        return [...comments].sort((a, b) => {
            const aDate = a.createdDate ? Date.parse(a.createdDate) : 0;
            const bDate = b.createdDate ? Date.parse(b.createdDate) : 0;
            if (aDate !== bDate) {
                return aDate - bDate;
            }
            return (a.id ?? 0) - (b.id ?? 0);
        });
    });
    readonly renderedComments = computed(() => {
        return this.orderedComments().map((comment) => ({
            comment,
            authorName: comment.authorName,
            isEdited: this.isEdited(comment),
            displayText: this.formatReviewCommentText(comment),
        }));
    });
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

    /**
     * Deletes the given comment via the review comment service.
     *
     * @param commentId The id of the comment to delete.
     */
    deleteComment(commentId: number): void {
        this.reviewCommentService.deleteCommentInContext(commentId);
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
     * Toggles the thread body and emits the collapsed state.
     */
    toggleThreadBody(): void {
        const nextVisibleState = !this.showThreadBody();
        this.showThreadBody.set(nextVisibleState);
        this.onToggleCollapse.emit(!nextVisibleState);
    }

    ngOnInit(): void {
        this.showThreadBody.set(!this.initialCollapsed());
        this.updateMenuItems();
        this.translateService.onLangChange.pipe(takeUntil(this.destroyed$)).subscribe(() => {
            this.updateMenuItems();
            this.changeDetectorRef.detectChanges();
        });
    }

    ngOnDestroy(): void {
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
            this.deleteComment(comment.id);
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
    }

    private updateMenuItems(): void {
        this.userCommentMenuItems = [
            { id: 'edit', label: this.translateService.instant('artemisApp.review.editComment') },
            { id: 'delete', label: this.translateService.instant('artemisApp.review.deleteComment') },
        ];
        this.nonUserCommentMenuItems = [{ id: 'delete', label: this.translateService.instant('artemisApp.review.deleteComment') }];
    }
}
