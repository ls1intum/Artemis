import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { Post } from 'app/entities/metis/post.model';
import { PostVotesAction, PostVotesActionName } from 'app/overview/postings/post-votes/post-votes.component';
import { PostingService } from 'app/overview/postings/posting.service';

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
    styleUrls: ['../postings.scss'],
})
export class PostComponent implements OnInit {
    @Input() post: Post;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Output() interactPost = new EventEmitter<PostAction>();
    postEditContent?: string;
    maxPostContentLength = 1000;
    isEditMode: boolean;
    isLoading = false;
    courseId: number;

    // Only allow certain html tags and attributes
    allowedHtmlTags: string[] = ['a', 'b', 'strong', 'i', 'em', 'mark', 'small', 'del', 'ins', 'sub', 'sup', 'p', 'ins', 'blockquote', 'pre', 'code', 'span'];
    allowedHtmlAttributes: string[] = ['href', 'class', 'id'];

    constructor(private postingService: PostingService, private route: ActivatedRoute) {}

    /**
     * checks if the user is the author of the post
     * sets the post content as the editor text
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.postEditContent = this.post.content;
    }

    /**
     * pass the post to the row to delete
     */
    deletePost(): void {
        this.interactPost.emit({
            name: PostActionName.DELETE,
            post: this.post,
        });
    }

    /**
     * Changes the post content
     */
    savePost(): void {
        this.isLoading = true;
        this.post.content = this.postEditContent;
        this.postingService.update(this.courseId, this.post).subscribe({
            next: () => {
                this.isEditMode = false;
            },
            complete: () => {
                this.isLoading = false;
            },
        });
    }

    /**
     * toggles the edit Mode
     * set the editor text to the post content
     */
    toggleEditMode(): void {
        this.isEditMode = !this.isEditMode;
        this.postEditContent = this.post.content;
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
        this.postingService.updateVotes(this.courseId, this.post.id!, voteChange).subscribe((res) => {
            this.post = res.body!;
            this.interactPost.emit({
                name: PostActionName.VOTE_CHANGE,
                post: this.post,
            });
        });
    }

    /**
     * Takes a post and determines if the user is the author of it
     * @param {Post} post
     * @returns {boolean}
     */
    isAuthorOfPost(post: Post): boolean {
        return this.user ? post.author!.id === this.user.id : false;
    }
}
