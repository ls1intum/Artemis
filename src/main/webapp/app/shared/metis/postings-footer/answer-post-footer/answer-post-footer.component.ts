import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { PostingsFooterDirective } from 'app/shared/metis/postings-footer/postings-footer.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-answer-post-footer',
    templateUrl: './answer-post-footer.component.html',
    styleUrls: ['./answer-post-footer.component.scss'],
})
export class AnswerPostFooterComponent extends PostingsFooterDirective<AnswerPost> implements OnInit {
    @Output() toggleApproveChange: EventEmitter<AnswerPost> = new EventEmitter<AnswerPost>();
    isAtLeastTutorInCourse: boolean;
    isAuthorOfOriginalPost: boolean;

    constructor(private metisService: MetisService) {
        super();
    }

    /**
     * on initialization: invoke the metis service to determine if current user is at least tutor in course
     */
    ngOnInit(): void {
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        // determines if the current user is the author of the original post, that the answer belongs to
        this.isAuthorOfOriginalPost = this.metisService.metisUserIsAuthorOfPosting(this.posting.post!);
    }

    /**
     * toggles the resolvesPost property of an answer post if the user is at least tutor in a course or the user is the author of the original post,
     * delegates the update to the metis service
     */
    toggleResolvesPost(): void {
        if (this.isAtLeastTutorInCourse || this.isAuthorOfOriginalPost) {
            this.posting.resolvesPost = !this.posting.resolvesPost;
            this.metisService.updateAnswerPost(this.posting).subscribe(() => {});
        }
    }
}
