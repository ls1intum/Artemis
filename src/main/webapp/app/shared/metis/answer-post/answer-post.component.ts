import { Component, EventEmitter, Output } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { AnswerPostService } from 'app/shared/metis/answer-post/answer-post.service';
import { PostingDirective } from '../posting.directive';

@Component({
    selector: 'jhi-answer-post',
    templateUrl: './answer-post.component.html',
    styleUrls: ['../../../overview/discussion/discussion.scss'],
})
export class AnswerPostComponent extends PostingDirective<AnswerPost> {
    @Output() onDelete: EventEmitter<AnswerPost> = new EventEmitter<AnswerPost>();
    @Output() toggleApproveChange: EventEmitter<AnswerPost> = new EventEmitter<AnswerPost>();

    constructor(protected answerPostService: AnswerPostService) {
        super();
    }
}
