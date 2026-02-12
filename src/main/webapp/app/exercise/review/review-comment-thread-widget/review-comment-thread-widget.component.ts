import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, ViewEncapsulation, computed, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowUpRightFromSquare, faPen, faTrash, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { CommentThread } from 'app/exercise/shared/entities/review/comment-thread.model';
import { Comment, CommentType, CreateComment, UpdateCommentContent } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContent } from 'app/exercise/shared/entities/review/comment-content.model';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { takeUntil } from 'rxjs/operators';

@Component({
    selector: 'jhi-review-comment-thread-widget',
    templateUrl: './review-comment-thread-widget.component.html',
    styleUrls: ['./review-comment-thread-widget.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    imports: [FormsModule, ArtemisTranslatePipe, ArtemisDatePipe, NgbDropdown, NgbDropdownToggle, NgbDropdownMenu, FaIconComponent],
})
export class ReviewCommentThreadWidgetComponent implements OnInit, OnDestroy {
    readonly thread = input.required<CommentThread>();
    readonly initialCollapsed = input<boolean>(false);
    readonly showLocationWarning = input<boolean>(false);

    readonly onDelete = output<number>();
    readonly onReply = output<CreateComment>();
    readonly onUpdate = output<{ commentId: number; content: UpdateCommentContent }>();
    readonly onToggleResolved = output<boolean>();
    readonly onToggleCollapse = output<boolean>();

    replyText = '';
    protected readonly faTrash = faTrash;
    protected readonly faPen = faPen;
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    showThreadBody = true;
    editingCommentId?: number;
    editingCommentType?: CommentType;
    editText = '';

    private readonly destroyed$ = new Subject<void>();
    private readonly translateService = inject(TranslateService);
    private readonly changeDetectorRef = inject(ChangeDetectorRef);
    readonly renderedComments = computed(() => {
        return this.orderedComments().map((comment) => ({
            comment,
            authorName: comment.authorName,
            isEdited: this.isEdited(comment),
            displayText: this.formatReviewCommentText(comment),
        }));
    });

    /**
     * Emits a delete event for the given comment id.
     *
     * @param commentId The id of the comment to delete.
     */
    deleteComment(commentId: number): void {
        this.onDelete.emit(commentId);
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
        this.editingCommentId = comment.id;
        this.editingCommentType = comment.type;
        this.editText = this.formatReviewCommentText(comment);
    }

    /**
     * Cancels the current edit and clears the editor state.
     */
    cancelEditing(): void {
        this.editingCommentId = undefined;
        this.editingCommentType = undefined;
        this.editText = '';
    }

    /**
     * Saves the edited comment text when non-empty and emits an update event.
     */
    saveEditing(): void {
        const id = this.editingCommentId;
        const trimmed = this.editText.trim();
        if (id === undefined || !trimmed || this.editingCommentType !== CommentType.USER) {
            return;
        }
        this.onUpdate.emit({ commentId: id, content: { contentType: 'USER', text: trimmed } });
        this.cancelEditing();
    }

    /**
     * Emits a reply event with trimmed text and clears the reply field.
     */
    submitReply(): void {
        const trimmed = this.replyText.trim();
        if (!trimmed) {
            return;
        }
        this.onReply.emit({ contentType: 'USER', text: trimmed });
        this.replyText = '';
    }

    /**
     * Toggles the resolved state and collapses the thread when resolved.
     */
    toggleResolved(): void {
        const thread = this.thread();
        const nextResolved = !thread.resolved;
        this.onToggleResolved.emit(nextResolved);
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
        return this.editingCommentId === comment.id;
    }

    isUserComment(comment: Comment): boolean {
        return comment.type === CommentType.USER;
    }
}
