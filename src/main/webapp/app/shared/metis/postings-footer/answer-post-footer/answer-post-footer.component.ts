import { Component, EventEmitter, Output } from '@angular/core';
import { PostingsFooterDirective } from 'app/shared/metis/postings-footer/postings-footer.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { AnswerPostService } from 'app/shared/metis/answer-post/answer-post.service';

@Component({
    selector: 'jhi-answer-post-footer',
    templateUrl: './answer-post-footer.component.html',
    styleUrls: ['../../../../overview/discussion/discussion.scss'],
})
export class AnswerPostFooterComponent extends PostingsFooterDirective<AnswerPost> {
    @Output() toggleApproveChange: EventEmitter<AnswerPost> = new EventEmitter<AnswerPost>();

    constructor(protected answerPostService: AnswerPostService) {
        super(answerPostService);
    }

    toggleApprove(): void {
        if (this.isAtLeastTutorInCourse) {
            this.posting.tutorApproved = !this.posting.tutorApproved;
            this.postingService.update(this.courseId, this.posting).subscribe(() => {
                this.toggleApproveChange.emit(this.posting);
            });
        }
    }
}
