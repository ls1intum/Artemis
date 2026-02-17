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
    viewChildren,
} from '@angular/core';
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
import { CommentContent } from 'app/exercise/shared/entities/review/comment-content.model';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { takeUntil } from 'rxjs/operators';
import { ReviewCommentFacade } from 'app/exercise/review/review-comment-facade.service';

@Component({
    selector: 'jhi-review-comment-thread-widget',
    templateUrl: './review-comment-thread-widget.component.html',
    styleUrls: ['./review-comment-thread-widget.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    imports: [FormsModule, ButtonDirective, MenuModule, ArtemisTranslatePipe, ArtemisDatePipe, FaIconComponent],
})
export class ReviewCommentThreadWidgetComponent implements OnInit, OnDestroy {
    readonly thread = input.required<CommentThread>();
    readonly initialCollapsed = input<boolean>(false);
    readonly showLocationWarning = input<boolean>(false);
    readonly onToggleCollapse = output<boolean>();

    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faEllipsisVertical = faEllipsisVertical;
    protected readonly faPen = faPen;
    protected readonly faTrash = faTrash;
    showThreadBody = true;
    readonly editingCommentId = signal<number | undefined>(undefined);
    readonly editingCommentType = signal<CommentType | undefined>(undefined);
    readonly userCommentMenuItems: MenuItem[] = [{ id: 'edit' }, { id: 'delete' }];
    readonly nonUserCommentMenuItems: MenuItem[] = [{ id: 'delete' }];
    private readonly commentMenus = viewChildren(Menu);

    private readonly destroyed$ = new Subject<void>();
    private readonly translateService = inject(TranslateService);
    private readonly changeDetectorRef = inject(ChangeDetectorRef);
    private readonly reviewCommentFacade = inject(ReviewCommentFacade);
    readonly renderedComments = computed(() => {
        return this.orderedComments().map((comment) => ({
            comment,
            authorName: comment.authorName,
            isEdited: this.isEdited(comment),
            displayText: this.formatReviewCommentText(comment),
        }));
    });
    readonly replyText = computed(() => this.reviewCommentFacade.getReplyDraft(this.thread().id));
    readonly isReplySubmitting = computed(() => this.reviewCommentFacade.isReplySubmitting(this.thread().id));
    readonly isResolveSubmitting = computed(() => this.reviewCommentFacade.isResolveSubmitting(this.thread().id));
    readonly editText = computed(() => {
        const commentId = this.editingCommentId();
        return commentId !== undefined ? this.reviewCommentFacade.getEditDraft(commentId) : '';
    });
    readonly isEditSubmitting = computed(() => {
        const commentId = this.editingCommentId();
        return commentId !== undefined ? this.reviewCommentFacade.isEditSubmitting(commentId) : false;
    });

    constructor() {
        effect(() => {
            const editingCommentId = this.editingCommentId();
            if (editingCommentId === undefined) {
                return;
            }
            const commentStillExists = (this.thread().comments ?? []).some((comment) => comment.id === editingCommentId);
            if (!commentStillExists) {
                this.editingCommentId.set(undefined);
                this.editingCommentType.set(undefined);
            }
        });
    }

    /**
     * Deletes the given comment if no delete operation for it is currently pending.
     *
     * @param commentId The id of the comment to delete.
     */
    deleteComment(commentId: number): void {
        if (this.reviewCommentFacade.isDeleteSubmitting(commentId)) {
            return;
        }
        this.reviewCommentFacade.deleteComment(commentId);
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
        this.reviewCommentFacade.startEditDraft(comment.id, this.formatReviewCommentText(comment));
    }

    /**
     * Cancels the current edit and clears the editor state.
     */
    cancelEditing(): void {
        const editingCommentId = this.editingCommentId();
        this.editingCommentId.set(undefined);
        this.editingCommentType.set(undefined);
        if (editingCommentId !== undefined) {
            this.reviewCommentFacade.cancelEditDraft(editingCommentId);
        }
    }

    /**
     * Submits the current edit draft if valid.
     */
    submitEdit(): void {
        const id = this.editingCommentId();
        if (id === undefined || this.editingCommentType() !== CommentType.USER || this.reviewCommentFacade.isEditSubmitting(id)) {
            return;
        }
        const text = this.reviewCommentFacade.getEditDraft(id).trim();
        if (!text) {
            return;
        }
        this.reviewCommentFacade.updateComment(id, { contentType: 'USER', text });
        this.editingCommentId.set(undefined);
        this.editingCommentType.set(undefined);
    }

    /**
     * Submits the current reply draft if valid.
     */
    submitReply(): void {
        const threadId = this.thread().id;
        if (this.reviewCommentFacade.isReplySubmitting(threadId)) {
            return;
        }
        const text = this.reviewCommentFacade.getReplyDraft(threadId).trim();
        if (!text) {
            return;
        }
        this.reviewCommentFacade.createReply(threadId, { contentType: 'USER', text });
    }

    /**
     * Stores reply draft text in the review-comment store.
     *
     * @param text The updated reply text.
     */
    onReplyDraftChanged(text: string): void {
        this.reviewCommentFacade.setReplyDraft(this.thread().id, text);
    }

    /**
     * Stores edit draft text in the review-comment store.
     *
     * @param text The updated edit text.
     */
    onEditDraftChanged(text: string): void {
        const editingCommentId = this.editingCommentId();
        if (editingCommentId === undefined) {
            return;
        }
        this.reviewCommentFacade.setEditDraft(editingCommentId, text);
    }

    /**
     * Toggles the resolved state and collapses the thread when resolved.
     */
    toggleResolved(): void {
        const thread = this.thread();
        if (this.reviewCommentFacade.isResolveSubmitting(thread.id)) {
            return;
        }
        const nextResolved = !thread.resolved;
        this.reviewCommentFacade.toggleResolved(thread.id, nextResolved);
        if (nextResolved) {
            this.showThreadBody = false;
            this.onToggleCollapse.emit(true);
        }
    }

    /**
     * Toggles the thread body and emits the collapsed state.
     */
    toggleThreadBody(): void {
        this.showThreadBody = !this.showThreadBody;
        this.onToggleCollapse.emit(!this.showThreadBody);
    }

    ngOnInit(): void {
        this.showThreadBody = !this.initialCollapsed();
        this.translateService.onLangChange.pipe(takeUntil(this.destroyed$)).subscribe(() => this.changeDetectorRef.detectChanges());
    }

    ngOnDestroy(): void {
        this.destroyed$.next();
        this.destroyed$.complete();
    }

    /**
     * Hides all open comment action menus in this thread widget.
     */
    hideCommentMenus(): void {
        this.commentMenus().forEach((menu) => menu.hide());
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
     * Returns comments ordered by creation date, falling back to id order.
     *
     * @returns The sorted comments for the thread.
     */
    orderedComments(): Comment[] {
        const comments = this.thread().comments ?? [];
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
        if ('contentType' in content && content.contentType === 'CONSISTENCY_CHECK') {
            const severity = content.severity ?? '';
            const category = content.category ?? '';
            const text = content.text ?? '';
            return [severity, category, text].filter(Boolean).join(' - ');
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

    isUserComment(comment: Comment): boolean {
        return comment.type === CommentType.USER;
    }
}
