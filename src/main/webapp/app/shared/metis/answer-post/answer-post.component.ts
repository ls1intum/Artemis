import { Component, EventEmitter, Input, Output, ViewChild, ViewContainerRef } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-answer-post',
    templateUrl: './answer-post.component.html',
    styleUrls: ['./answer-post.component.scss'],
})
export class AnswerPostComponent extends PostingDirective<AnswerPost> {
    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() isLastAnswer: boolean;
    @Output() openPostingCreateEditModal = new EventEmitter<void>();

    @Input()
    isReadOnlyMode = false;
    // ng-container to render answerPostCreateEditModalComponent
    @ViewChild('createEditAnswerPostContainer', { read: ViewContainerRef }) containerRef: ViewContainerRef;
}
