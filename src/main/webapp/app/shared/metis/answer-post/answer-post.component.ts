import { Component, EventEmitter, Output } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { AnswerPostService } from 'app/shared/metis/answer-post/answer-post.service';
import { PostingDirective } from '../posting.directive';

export interface AnswerPostAction {
    name: AnswerPostActionName;
    answerPost: AnswerPost;
}

export enum AnswerPostActionName {
    DELETE,
    ADD,
    APPROVE,
}

@Component({
    selector: 'jhi-answer-post',
    templateUrl: './answer-post.component.html',
    styleUrls: ['../../../overview/discussion/discussion.scss'],
})
export class AnswerPostComponent extends PostingDirective<AnswerPost> {
    @Output() interactAnswerPost: EventEmitter<AnswerPostAction> = new EventEmitter<AnswerPostAction>();

    constructor(protected answerPostService: AnswerPostService) {
        super(answerPostService);
    }

    /**
     * Toggles the tutorApproved field for this posting
     */
    toggleAnswerPostTutorApproved(): void {
        this.posting.tutorApproved = !this.posting.tutorApproved;
        this.answerPostService.update(this.courseId, this.posting).subscribe(() => {
            this.interactAnswerPost.emit({
                name: AnswerPostActionName.APPROVE,
                answerPost: this.posting,
            });
        });
    }
}
