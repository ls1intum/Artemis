import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, TemplateRef, ViewChild } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { faComments, faEyeSlash } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-posting-thread',
    templateUrl: './posting-thread.component.html',
    styleUrls: ['../metis.component.scss'],
})
export class PostingThreadComponent implements OnInit, OnChanges, OnDestroy {
    @Input() post: Post;
    @Input() courseMessagesPageFlag: boolean;
    @ViewChild(AnswerPostCreateEditModalComponent) answerPostCreateEditModal?: AnswerPostCreateEditModalComponent;
    @Input() showAnswers: boolean;
    @Output() isModalOpen = new EventEmitter<void>();

    sortedAnswerPosts: AnswerPost[];
    createdAnswerPost: AnswerPost;
    isAtLeastTutorInCourse: boolean;
    @ViewChild('createAnswerPostModal') createAnswerPostModal: TemplateRef<AnswerPostCreateEditModalComponent>;

    // Icons
    faComments = faComments;
    faEyeSlash = faEyeSlash;

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
     * on leaving the page, the modal should be closed
     */
    ngOnDestroy(): void {
        this.answerPostCreateEditModal?.modalRef?.close();
    }

    /**
     * sorts answerPosts by two criteria
     * 1. criterion: resolvesPost -> true comes first
     * 2. criterion: creationDate -> most recent comes at the end (chronologically from top to bottom)
     */
    sortAnswerPosts(): void {
        if (!this.post.answers) {
            this.sortedAnswerPosts = [];
            return;
        }
        this.sortedAnswerPosts = this.post.answers.sort(
            (answerPostA, answerPostB) =>
                Number(answerPostB.resolvesPost) - Number(answerPostA.resolvesPost) || answerPostA.creationDate!.valueOf() - answerPostB.creationDate!.valueOf(),
        );
    }

    /**
     * creates empty default answer post that is needed on initialization of a newly opened modal to edit or create an answer post, with accordingly set resolvesPost flag
     * @return AnswerPost created empty default answer post
     */
    createEmptyAnswerPost(): AnswerPost {
        const answerPost = new AnswerPost();
        answerPost.content = '';
        answerPost.post = this.post;
        answerPost.resolvesPost = this.isAtLeastTutorInCourse;
        return answerPost;
    }
}
