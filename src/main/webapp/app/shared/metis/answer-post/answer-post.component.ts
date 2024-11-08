import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, ViewChild, ViewContainerRef } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import dayjs from 'dayjs/esm';
import { animate, style, transition, trigger } from '@angular/animations';
import { faBookmark } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-answer-post',
    templateUrl: './answer-post.component.html',
    styleUrls: ['./answer-post.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    animations: [
        trigger('fade', [
            transition(':enter', [style({ opacity: 0 }), animate('300ms ease-in', style({ opacity: 1 }))]),
            transition(':leave', [animate('300ms ease-out', style({ opacity: 0 }))]),
        ]),
    ],
})
export class AnswerPostComponent extends PostingDirective<AnswerPost> {
    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() isLastAnswer: boolean;
    @Output() openPostingCreateEditModal = new EventEmitter<void>();
    @Output() userReferenceClicked = new EventEmitter<string>();
    @Output() channelReferenceClicked = new EventEmitter<number>();
    isAnswerPost = true;

    @Input()
    isReadOnlyMode = false;
    // ng-container to render answerPostCreateEditModalComponent

    // Icons
    faBookmark = faBookmark;

    @ViewChild('createEditAnswerPostContainer', { read: ViewContainerRef }) containerRef: ViewContainerRef;
}
