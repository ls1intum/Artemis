import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, ViewChild, ViewContainerRef } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import dayjs from 'dayjs/esm';
import { Posting } from 'app/entities/metis/posting.model';
import { Reaction } from 'app/entities/metis/reaction.model';

@Component({
    selector: 'jhi-answer-post',
    templateUrl: './answer-post.component.html',
    styleUrls: ['./answer-post.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnswerPostComponent extends PostingDirective<AnswerPost> {
    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() isLastAnswer: boolean;
    @Output() openPostingCreateEditModal = new EventEmitter<void>();
    @Output() userReferenceClicked = new EventEmitter<string>();
    @Output() channelReferenceClicked = new EventEmitter<number>();

    @Input()
    isReadOnlyMode = false;
    // ng-container to render answerPostCreateEditModalComponent
    @ViewChild('createEditAnswerPostContainer', { read: ViewContainerRef }) containerRef: ViewContainerRef;
    @Input() isConsecutive: boolean = false;

    onPostingUpdated(updatedPosting: Posting) {
        this.posting = updatedPosting;
    }

    onReactionsUpdated(updatedReactions: Reaction[]) {
        this.posting = { ...this.posting, reactions: updatedReactions };
    }
}
