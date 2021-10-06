import { Component, OnInit } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingsHeaderDirective } from 'app/shared/metis/postings-header/postings-header.directive';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-answer-post-header',
    templateUrl: './answer-post-header.component.html',
    styleUrls: ['../posting-header.component.scss'],
})
export class AnswerPostHeaderComponent extends PostingsHeaderDirective<AnswerPost> implements OnInit {
    isAuthorOfOriginalPost: boolean;

    constructor(protected metisService: MetisService) {
        super(metisService);
    }

    ngOnInit() {
        super.ngOnInit();
        // determines if the current user is the author of the original post, that the answer belongs to
        this.isAuthorOfOriginalPost = this.metisService.metisUserIsAuthorOfPosting(this.posting.post!);
    }

    /**
     * invokes the metis service to delete an answer post
     */
    deletePosting(): void {
        this.metisService.deleteAnswerPost(this.posting);
    }

    /**
     * toggles the resolvesPost property of an answer post if the user is at least tutor in a course or the user is the author of the original post,
     * delegates the update to the metis service
     */
    toggleResolvesPost(): void {
        if (this.isAtLeastTutorInCourse || this.isAuthorOfOriginalPost) {
            this.posting.resolvesPost = !this.posting.resolvesPost;
            this.metisService.updateAnswerPost(this.posting).subscribe();
        }
    }
}
