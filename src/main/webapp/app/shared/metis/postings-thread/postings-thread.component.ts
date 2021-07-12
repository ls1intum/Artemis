import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { LocalStorageService } from 'ngx-webstorage';
import { PostAction, PostActionName } from 'app/shared/metis/post/post.component';
import { AnswerPostService } from 'app/shared/metis/answer-post/answer-post.service';
import { PostService } from 'app/shared/metis/post/post.service';
import { HttpResponse } from '@angular/common/http';

export interface PostRowAction {
    name: PostRowActionName;
    post: Post;
}

export enum PostRowActionName {
    VOTE_CHANGE,
}

@Component({
    selector: 'jhi-postings-thread',
    templateUrl: './postings-thread.component.html',
    styleUrls: ['../../../overview/discussion/discussion.scss'],
})
export class PostingsThreadComponent implements OnInit {
    @Input() post: Post;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Input() courseId: number;
    @Input() existingPostTags: string[];
    @Output() onDelete: EventEmitter<Post> = new EventEmitter<Post>();
    @Output() interactPostRow: EventEmitter<PostRowAction> = new EventEmitter<PostRowAction>();
    toggledAnswers: boolean;
    sortedAnswerPosts: AnswerPost[];

    constructor(private answerPostService: AnswerPostService, private postService: PostService, private localStorage: LocalStorageService) {}

    /**
     * sort answers when component is initialized
     */
    ngOnInit(): void {
        this.sortAnswerPosts();
    }

    /**
     * interact with post component
     * @param {PostAction} action
     */
    interactPost(action: PostAction): void {
        switch (action.name) {
            case PostActionName.VOTE_CHANGE:
                this.interactPostRow.emit({
                    name: PostRowActionName.VOTE_CHANGE,
                    post: action.post,
                });
                break;
        }
    }

    /**
     * sorts the answer posts of a post into approved and not approved and then by date
     */
    sortAnswerPosts(): void {
        if (!this.post.answers) {
            this.sortedAnswerPosts = [];
            return;
        }
        this.sortedAnswerPosts = this.post.answers.sort(
            (answerPostA, answerPostB) =>
                Number(answerPostB.tutorApproved) - Number(answerPostA.tutorApproved) || answerPostA.creationDate!.valueOf() - answerPostB.creationDate!.valueOf(),
        );
    }

    deletePost(): void {
        this.onDelete.emit(this.post);
        this.localStorage.clear(`q${this.post.id}u${this.user.id}`);
    }

    onCreateAnswerPost(answerPost: AnswerPost): void {
        if (!this.post.answers) {
            this.post.answers = [];
        }
        this.post.answers.push(answerPost);
        this.sortAnswerPosts();
    }

    deleteAnswerPost(answerPost: AnswerPost): void {
        this.post.answers = this.post.answers?.filter((el: AnswerPost) => el.id !== answerPost.id);
        this.sortAnswerPosts();
    }

    createEmptyAnswerPost(): AnswerPost {
        const answerPost = new AnswerPost();
        answerPost.content = '';
        answerPost.post = this.post;
        answerPost.tutorApproved = this.isAtLeastTutorInCourse;
        return answerPost;
    }

    toggleAnswers() {
        this.toggledAnswers = !this.toggledAnswers;
    }

    toggleApprove(changedAnswerPost: AnswerPost): void {
        const updatedAnswerPost = this.post.answers?.find((answerPost: AnswerPost) => answerPost.id === changedAnswerPost.id)!;
        updatedAnswerPost.tutorApproved = changedAnswerPost.tutorApproved;
        this.sortAnswerPosts();
    }
}
