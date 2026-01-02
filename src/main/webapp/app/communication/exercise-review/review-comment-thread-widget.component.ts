import { ChangeDetectionStrategy, Component, ViewEncapsulation, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';

export interface ReviewCommentThreadComment {
    id: number;
    authorName: string;
    text: string;
}

@Component({
    selector: 'jhi-review-comment-thread-widget',
    templateUrl: './review-comment-thread-widget.component.html',
    styleUrls: ['./review-comment-widget.shared.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    imports: [FormsModule, ArtemisTranslatePipe, NgbDropdown, NgbDropdownToggle, NgbDropdownMenu],
})
export class ReviewCommentThreadWidgetComponent {
    readonly comments = input<ReviewCommentThreadComment[]>([]);

    readonly onDelete = output<number>();
    readonly onReply = output<string>();

    replyText = '';

    deleteComment(commentId: number): void {
        this.onDelete.emit(commentId);
    }

    submitReply(): void {
        const trimmed = this.replyText.trim();
        if (!trimmed) {
            return;
        }
        this.onReply.emit(trimmed);
        this.replyText = '';
    }
}
