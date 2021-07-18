import { Component, EventEmitter, Output } from '@angular/core';
import { PostingsFooterDirective } from 'app/shared/metis/postings-footer/postings-footer.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { AnswerPostService } from 'app/shared/metis/answer-post/answer-post.service';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-answer-post-footer',
    templateUrl: './answer-post-footer.component.html',
    styleUrls: ['../../../../overview/discussion/discussion.scss'],
})
export class AnswerPostFooterComponent extends PostingsFooterDirective<AnswerPost> {
    @Output() toggleApproveChange: EventEmitter<AnswerPost> = new EventEmitter<AnswerPost>();

    constructor(protected answerPostService: AnswerPostService, protected metisService: MetisService) {
        super(answerPostService, metisService);
    }

    toggleApprove(): void {
        if (this.isAtLeastTutorInCourse) {
            this.posting.tutorApproved = !this.posting.tutorApproved;
            this.metisService.updateAnswerPost(this.posting);
        }
    }
}
