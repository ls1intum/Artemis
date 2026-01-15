import { ChangeDetectionStrategy, Component, OnInit, ViewEncapsulation, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPen, faTrash } from '@fortawesome/free-solid-svg-icons';
import { CommentThread, CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { Comment } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContent } from 'app/exercise/shared/entities/review/comment-content.model';

@Component({
    selector: 'jhi-review-comment-thread-widget',
    templateUrl: './review-comment-thread-widget.component.html',
    styleUrls: ['./review-comment-widget.shared.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    imports: [FormsModule, ArtemisTranslatePipe, ArtemisDatePipe, NgbDropdown, NgbDropdownToggle, NgbDropdownMenu, FaIconComponent],
})
export class ReviewCommentThreadWidgetComponent implements OnInit {
    readonly thread = input<CommentThread | undefined>();
    readonly initialCollapsed = input<boolean>(false);

    readonly onDelete = output<number>();
    readonly onReply = output<string>();
    readonly onUpdate = output<{ commentId: number; text: string }>();
    readonly onToggleResolved = output<boolean>();
    readonly onToggleCollapse = output<boolean>();

    replyText = '';
    protected readonly faTrash = faTrash;
    protected readonly faPen = faPen;
    showThreadBody = true;
    editingCommentId?: number;
    editText = '';

    deleteComment(commentId: number): void {
        this.onDelete.emit(commentId);
    }

    startEditing(comment: Comment): void {
        this.editingCommentId = comment.id;
        this.editText = this.formatReviewCommentText(comment);
    }

    cancelEditing(): void {
        this.editingCommentId = undefined;
        this.editText = '';
    }

    saveEditing(): void {
        const id = this.editingCommentId;
        const trimmed = this.editText.trim();
        if (id === undefined || !trimmed) {
            return;
        }
        this.onUpdate.emit({ commentId: id, text: trimmed });
        this.cancelEditing();
    }

    submitReply(): void {
        const trimmed = this.replyText.trim();
        if (!trimmed) {
            return;
        }
        this.onReply.emit(trimmed);
        this.replyText = '';
    }

    toggleResolved(): void {
        const thread = this.thread();
        if (!thread) {
            return;
        }
        this.onToggleResolved.emit(!thread.resolved);
    }

    toggleThreadBody(): void {
        this.showThreadBody = !this.showThreadBody;
        this.onToggleCollapse.emit(!this.showThreadBody);
    }

    getThreadLineLabel(): string {
        const lineNumber = this.thread()?.lineNumber;
        return lineNumber ? `${lineNumber}` : '-';
    }

    ngOnInit(): void {
        this.showThreadBody = !this.initialCollapsed();
    }

    isEdited(comment: Comment): boolean {
        if (!comment.lastModifiedDate || !comment.createdDate) {
            return false;
        }
        return comment.lastModifiedDate !== comment.createdDate;
    }

    orderedComments(): Comment[] {
        const comments = this.thread()?.comments ?? [];
        return [...comments].sort((a, b) => {
            const aDate = a.createdDate ? Date.parse(a.createdDate) : 0;
            const bDate = b.createdDate ? Date.parse(b.createdDate) : 0;
            if (aDate !== bDate) {
                return aDate - bDate;
            }
            return (a.id ?? 0) - (b.id ?? 0);
        });
    }

    formatReviewCommentText(comment: Comment): string {
        const content = comment.content as CommentContent | undefined;
        if (!content) {
            return '';
        }
        if (content.contentType === 'USER') {
            return content.text ?? '';
        }
        const severity = content.severity ?? '';
        const category = content.category ?? '';
        const description = content.description ?? '';
        return [severity, category, description].filter(Boolean).join(' - ');
    }

    getCommentAuthorName(comment: Comment): string {
        if (comment.authorName) {
            return comment.authorName;
        }
        if (comment.authorId !== undefined && comment.authorId !== null) {
            return `User ${comment.authorId}`;
        }
        return '';
    }

    isEditing(comment: Comment): boolean {
        return this.editingCommentId === comment.id;
    }

    getThreadMeta(): { key: string; params: { versionId?: number; commitSha?: string } } | undefined {
        const thread = this.thread();
        if (!thread) {
            return undefined;
        }

        if (thread.targetType === CommentThreadLocationType.PROBLEM_STATEMENT && thread.initialVersionId) {
            return { key: 'artemisApp.review.threadVersion', params: { versionId: thread.initialVersionId } };
        }
        if (thread.targetType !== CommentThreadLocationType.PROBLEM_STATEMENT && thread.initialCommitSha) {
            return { key: 'artemisApp.review.threadCommit', params: { commitSha: thread.initialCommitSha } };
        }
        return undefined;
    }
}
