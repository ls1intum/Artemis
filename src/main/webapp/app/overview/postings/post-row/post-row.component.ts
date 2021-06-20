import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';
import * as moment from 'moment';
import { HttpResponse } from '@angular/common/http';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostService } from 'app/overview/postings/post/post.service';
import { AnswerPostService } from 'app/overview/postings/answer-post/answer-post.service';
import { LocalStorageService } from 'ngx-webstorage';
import { AnswerPostActionName, AnswerPostAction } from 'app/overview/postings/answer-post/answer-post.component';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { PostActionName, PostAction } from 'app/overview/postings/post/post.component';

export interface PostRowAction {
    name: PostRowActionName;
    post: Post;
}

export enum PostRowActionName {
    DELETE,
    VOTE_CHANGE,
}

@Component({
    selector: 'jhi-post-row',
    templateUrl: './post-row.component.html',
    styleUrls: ['../postings.scss'],
})
export class PostRowComponent implements OnInit {
    @Input() post: Post;
    @Input() selectedPost: Post;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Output() interactPostRow = new EventEmitter<PostRowAction>();
    isExpanded = true;
    isAnswerMode: boolean;
    isLoading = false;
    answerPostContent?: string;
    sortedAnswerPosts: AnswerPost[];
    approvedAnswerPosts: AnswerPost[];
    EditorMode = EditorMode;
    courseId: number;

    constructor(private answerPostService: AnswerPostService, private postService: PostService, private localStorage: LocalStorageService, private route: ActivatedRoute) {}

    /**
     * sort answers when component is initialized
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
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
            case PostActionName.EXPAND:
                this.isExpanded = !this.isExpanded;
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
            this.approvedAnswerPosts = [];
            return;
        }
        this.approvedAnswerPosts = this.post.answers
            .filter((ans) => ans.tutorApproved)
            .sort((a, b) => {
                const aValue = moment(a.creationDate!).valueOf();
                const bValue = moment(b.creationDate!).valueOf();

                return aValue - bValue;
            });
        this.sortedAnswerPosts = this.post.answers
            .filter((ans) => !ans.tutorApproved)
            .sort((a, b) => {
                const aValue = moment(a.creationDate!).valueOf();
                const bValue = moment(b.creationDate!).valueOf();

                return aValue - bValue;
            });
    }

    /**
     * deletes the post
     */
    deletePost(): void {
        this.postService.delete(this.courseId, this.post.id!).subscribe(() => {
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
    addAnswerPost(): void {
        this.isLoading = true;
        const answerPost = new AnswerPost();
        answerPost.content = this.answerPostContent;
        answerPost.post = this.post;
        answerPost.tutorApproved = false;
        answerPost.creationDate = moment();
        this.answerPostService.create(this.courseId, answerPost).subscribe({
            next: (postResponse: HttpResponse<AnswerPost>) => {
                if (!this.post.answers) {
                    this.post.answers = [];
                }
                this.post.answers.push(postResponse.body!);
                this.sortAnswerPosts();
                this.answerPostContent = undefined;
                this.isAnswerMode = false;
            },
            complete: () => {
                this.isLoading = false;
            },
        });
    }

    /**
     * Takes a answerPost and deletes it
     * @param   {answerPost} answerPost
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
}
