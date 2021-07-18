import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { LocalStorageService } from 'ngx-webstorage';
import { AnswerPostService } from 'app/shared/metis/answer-post/answer-post.service';
import { PostService } from 'app/shared/metis/post/post.service';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-postings-thread',
    templateUrl: './postings-thread.component.html',
    styleUrls: ['../../../overview/discussion/discussion.scss'],
})
export class PostingsThreadComponent implements OnInit, OnChanges {
    @Input() post: Post;
    @Input() courseId: number;
    showAnswers: boolean;
    sortedAnswerPosts: AnswerPost[];
    createdAnswerPost: AnswerPost;
    isAtLeastTutorInCourse: boolean;

    constructor(private answerPostService: AnswerPostService, private postService: PostService, private metisService: MetisService, private localStorage: LocalStorageService) {}

    /**
     * sort answers when component is initialized
     */
    ngOnInit(): void {
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.sortAnswerPosts();
        this.createdAnswerPost = this.createEmptyAnswerPost();
    }

    ngOnChanges(): void {
        this.sortAnswerPosts();
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

    createEmptyAnswerPost(): AnswerPost {
        const answerPost = new AnswerPost();
        answerPost.content = '';
        answerPost.post = this.post;
        answerPost.tutorApproved = this.isAtLeastTutorInCourse;
        return answerPost;
    }

    toggleAnswers() {
        this.showAnswers = !this.showAnswers;
    }
}
