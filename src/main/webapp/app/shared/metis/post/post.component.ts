import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostVotesAction, PostVotesActionName } from 'app/shared/metis/post/post-votes/post-votes.component';
import { PostService } from 'app/shared/metis/post/post.service';
import { PostingDirective } from 'app/shared/metis/posting.directive';

export interface PostAction {
    name: PostActionName;
    post: Post;
}

export enum PostActionName {
    VOTE_CHANGE,
}

@Component({
    selector: 'jhi-post',
    templateUrl: './post.component.html',
    styleUrls: ['../../../overview/discussion/discussion.scss'],
})
export class PostComponent extends PostingDirective<Post> implements OnInit {
    @Output() toggledAnswersChange: EventEmitter<void> = new EventEmitter<void>();
    @Output() interactPost: EventEmitter<PostAction> = new EventEmitter<PostAction>();
    @Input() existingPostTags: string[];

    constructor(protected postService: PostService) {
        super();
    }

    /**
     * interact with actions sent from postVotes
     * @param {PostVotesAction} action
     */
    interactVotes(action: PostVotesAction): void {
        switch (action.name) {
            case PostVotesActionName.VOTE_CHANGE:
                this.updateVotes(action.value);
                break;
        }
    }

    /**
     * update the number of votes for this post
     * @param {number} voteChange
     */
    updateVotes(voteChange: number): void {
        this.postService.updateVotes(this.courseId, this.posting.id!, voteChange).subscribe((res) => {
            this.posting = res.body!;
            this.interactPost.emit({
                name: PostActionName.VOTE_CHANGE,
                post: this.posting,
            });
        });
    }
}
