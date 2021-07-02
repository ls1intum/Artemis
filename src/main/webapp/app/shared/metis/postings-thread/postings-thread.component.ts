import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { User } from 'app/core/user/user.model';
import * as moment from 'moment';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { LocalStorageService } from 'ngx-webstorage';
import { AnswerPostAction, AnswerPostActionName } from 'app/shared/metis/answer-post/answer-post.component';
import { PostAction, PostActionName } from 'app/shared/metis/post/post.component';
import { AnswerPostService } from 'app/shared/metis/answer-post/answer-post.service';
import { PostService } from 'app/shared/metis/post/post.service';

export interface PostRowAction {
    name: PostRowActionName;
    post: Post;
}

export enum PostRowActionName {
    DELETE,
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
    @Output() interactPostRow = new EventEmitter<PostRowAction>();
    toggledAnswers = false;
    sortedAnswerPosts: AnswerPost[];

    constructor(private answerPostService: AnswerPostService, private postService: PostService, private localStorage: LocalStorageService) {}

    /**
     * sort answers when component is initialized
     */
    ngOnInit(): void {
        this.sortAnswerPosts();
    }

    /**
     * interact with answerPost component
     * @param {AnswerPostAction} action
     */
    interactAnswerPost(action: AnswerPostAction) {
        switch (action.name) {
            case AnswerPostActionName.DELETE:
                this.deleteAnswerFromList(action.answerPost);
                break;
            case AnswerPostActionName.ADD:
                this.addAnswerPostToList(action.answerPost);
                break;
            case AnswerPostActionName.APPROVE:
                this.sortAnswerPosts();
                break;
        }
    }

    /**
     * interact with post component
     * @param {PostAction} action
     */
    interactPost(action: PostAction): void {
        switch (action.name) {
            case PostActionName.DELETE:
                this.deletePost();
                break;
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

    /**
     * deletes the post
     */
    deletePost(): void {
        this.postService.delete(this.courseId, this.post).subscribe(() => {
            this.localStorage.clear(`q${this.post.id}u${this.user.id}`);
            this.interactPostRow.emit({
                name: PostRowActionName.DELETE,
                post: this.post,
            });
        });
    }

    /**
     * Creates a new answerPost
     */
    onCreateAnswerPost(answerPost: AnswerPost): void {
        if (!this.post.answers) {
            this.post.answers = [];
        }
        this.post.answers.push(answerPost);
        this.sortAnswerPosts();
    }

    /**
     * Takes a answerPost and deletes it
     * @param   {posting} answerPost
     */
    deleteAnswerFromList(answerPost: AnswerPost): void {
        this.post.answers = this.post.answers?.filter((el: AnswerPost) => el.id !== answerPost.id);
        this.sortAnswerPosts();
    }

    /**
     * Takes a answerPost and adds it to the others
     * @param   {AnswerPost} answerPost
     */
    addAnswerPostToList(answerPost: AnswerPost): void {
        this.post.answers!.push(answerPost);
        this.sortAnswerPosts();
    }

    createEmptyAnswerPost(): AnswerPost {
        const answerPost = new AnswerPost();
        answerPost.content = '';
        answerPost.post = this.post;
        answerPost.tutorApproved = false;
        return answerPost;
    }

    toggleAnswers() {
        this.toggledAnswers = !this.toggledAnswers;
    }
}
