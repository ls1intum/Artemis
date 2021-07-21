import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-postings-thread',
    templateUrl: './postings-thread.component.html',
    styleUrls: ['../../../overview/discussion/discussion.scss'],
})
export class PostingsThreadComponent implements OnInit, OnChanges {
    @Input() post: Post;
    showAnswers: boolean;
    sortedAnswerPosts: AnswerPost[];
    createdAnswerPost: AnswerPost;
    isAtLeastTutorInCourse: boolean;

    constructor(private metisService: MetisService) {}

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
     * Sorts answerPosts by two criteria
     * 1. Criterion: tutorApproved -> true comes first
     * 2. Criterion: creationDate -> most recent comes at the end (chronologically from top to bottom)
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
