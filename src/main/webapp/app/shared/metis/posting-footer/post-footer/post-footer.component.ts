import { Component, Input, OnChanges, OnDestroy, OnInit, ViewChild, ViewContainerRef } from '@angular/core';
import { PostingFooterDirective } from 'app/shared/metis/posting-footer/posting-footer.directive';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

@Component({
    selector: 'jhi-post-footer',
    templateUrl: './post-footer.component.html',
    styleUrls: ['./post-footer.component.scss'],
})
export class PostFooterComponent extends PostingFooterDirective<Post> implements OnInit, OnChanges, OnDestroy {
    @Input() previewMode: boolean;
    // if the post is previewed in the create/edit modal,
    // we need to pass the ref in order to close it when navigating to the previewed post via post context
    @Input() modalRef?: NgbModalRef;
    tags: string[];
    courseId: number;

    @ViewChild(AnswerPostCreateEditModalComponent) answerPostCreateEditModal?: AnswerPostCreateEditModalComponent;
    @Input() showAnswers: boolean;
    sortedAnswerPosts: AnswerPost[];
    createdAnswerPost: AnswerPost;
    isAtLeastTutorInCourse: boolean;

    // ng-container to render inlineInputComponent
    @ViewChild('inlineInputContainer', { read: ViewContainerRef }) inlineInputContainer: ViewContainerRef;

    constructor(private metisService: MetisService) {
        super();
    }

    /**
     * on initialization: updates the post tags and the context information
     */
    ngOnInit(): void {
        this.courseId = this.metisService.getCourse().id!;
        this.updateTags();
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.sortAnswerPosts();
        this.createdAnswerPost = this.createEmptyAnswerPost();
    }

    /**
     * on changes: updates the post tags and the context information
     */
    ngOnChanges(): void {
        this.updateTags();
        this.sortAnswerPosts();
    }

    /**
     * on leaving the page, the modal should be closed
     */
    ngOnDestroy(): void {
        this.answerPostCreateEditModal?.modalRef?.close();
    }

    /**
     * sets the current post tags, empty error if none exit
     */
    private updateTags(): void {
        if (this.posting.tags) {
            this.tags = this.posting.tags;
        } else {
            this.tags = [];
        }
    }

    /**
     * sorts answerPosts by two criteria
     * 1. criterion: resolvesPost -> true comes first
     * 2. criterion: creationDate -> most recent comes at the end (chronologically from top to bottom)
     */
    sortAnswerPosts(): void {
        if (!this.posting.answers) {
            this.sortedAnswerPosts = [];
            return;
        }
        this.sortedAnswerPosts = this.posting.answers.sort(
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
        answerPost.post = this.posting;
        answerPost.resolvesPost = this.isAtLeastTutorInCourse;
        return answerPost;
    }

    /**
     * defines a function that returns the answerPost id as unique identifier,
     * by this means, Angular determines which answerPost in the collection of answerPosts has to be reloaded/destroyed on changes
     */
    answerPostTrackByFn = (index: number, answerPost: AnswerPost): number => answerPost.id!;
}
