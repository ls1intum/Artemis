import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { PostingsFooterDirective } from 'app/shared/metis/postings-footer/postings-footer.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-answer-post-footer',
    templateUrl: './answer-post-footer.component.html',
    styleUrls: ['../../../../overview/discussion/discussion.scss'],
})
export class AnswerPostFooterComponent extends PostingsFooterDirective<AnswerPost> implements OnInit {
    @Output() toggleApproveChange: EventEmitter<AnswerPost> = new EventEmitter<AnswerPost>();
    isAtLeastTutorInCourse: boolean;

    constructor(protected metisService: MetisService) {
        super();
    }

    ngOnInit(): void {
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
    }

    toggleApprove(): void {
        if (this.isAtLeastTutorInCourse) {
            this.posting.tutorApproved = !this.posting.tutorApproved;
            this.metisService.updateAnswerPost(this.posting);
        }
    }
}
