import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { Post } from 'app/entities/metis/post.model';
import { PostVotesAction, PostVotesActionName } from 'app/shared/metis/post/post-votes/post-votes.component';
import { PostService } from 'app/shared/metis/post/post.service';
import { PostingComponent } from 'app/shared/metis/posting.component';

export interface PostAction {
    name: PostActionName;
    post: Post;
}

export enum PostActionName {
    DELETE,
    EXPAND,
    VOTE_CHANGE,
}

@Component({
    selector: 'jhi-post',
    templateUrl: './post.component.html',
    styleUrls: ['../../../overview/discussion/discussion.scss'],
})
export class PostComponent extends PostingComponent<Post> {
    @Output() interactPost: EventEmitter<PostAction> = new EventEmitter<PostAction>();

    constructor(protected postService: PostService, protected route: ActivatedRoute) {
        super(postService, route);
    }

    /**
     * toggles the edit Mode
     * set the editor text to the post content
     */
    toggleEditMode(): void {
        this.isEditMode = !this.isEditMode;
        this.content = this.posting.content;
    }

    onInteractPost($event: PostAction) {
        this.interactPost.emit($event);
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
