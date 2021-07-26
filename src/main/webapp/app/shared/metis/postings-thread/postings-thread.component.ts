import { Component, Input, OnChanges, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';

@Component({
    selector: 'jhi-postings-thread',
    templateUrl: './postings-thread.component.html',
    styleUrls: ['./postings-thread.scss'],
})
export class PostingsThreadComponent implements OnInit, OnChanges {
    @Input() post: Post;
    showAnswers: boolean;
    sortedAnswerPosts: AnswerPost[];
    createdAnswerPost: AnswerPost;
    isAtLeastTutorInCourse: boolean;
    @ViewChild('createAnswerPostModal') createAnswerPostModal: TemplateRef<AnswerPostCreateEditModalComponent>;

    constructor(private metisService: MetisService) {}

    /**
     * on initialization: determines if user is at least tutor in course by invoking metis service, sorts the answer posts,
     * and creates a default answer post
     */
    ngOnInit(): void {
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.sortAnswerPosts();
        this.createdAnswerPost = this.createEmptyAnswerPost();
    }

    /**
     * on changes: sorts the answer posts
     */
    ngOnChanges(): void {
        this.sortAnswerPosts();
    }

    /**
     * sorts answerPosts by two criteria
     * 1. criterion: tutorApproved -> true comes first
     * 2. criterion: creationDate -> most recent comes at the end (chronologically from top to bottom)
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
     * creates empty default answer post that is needed on initialization of a newly opened modal to edit or create an answer post, with accordingly set tutorApproved flag
     * @return AnswerPost created empty default answer post
     */
    createEmptyAnswerPost(): AnswerPost {
        const answerPost = new AnswerPost();
        answerPost.content = '';
        answerPost.post = this.post;
        answerPost.tutorApproved = this.isAtLeastTutorInCourse;
        return answerPost;
    }

    /**
     * toggles the answers of a post (show/do not show)
     */
    toggleAnswers(): void {
        this.showAnswers = !this.showAnswers;
    }
}
