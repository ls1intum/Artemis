import { AfterContentChecked, ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild, ViewContainerRef, inject } from '@angular/core';
import { PostingFooterDirective } from 'app/shared/metis/posting-footer/posting-footer.directive';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { AnswerPostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/answer-post-create-edit-modal/answer-post-create-edit-modal.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-post-footer',
    templateUrl: './post-footer.component.html',
    styleUrls: ['./post-footer.component.scss'],
})
export class PostFooterComponent extends PostingFooterDirective<Post> implements OnInit, OnDestroy, AfterContentChecked {
    private metisService = inject(MetisService);
    protected changeDetector = inject(ChangeDetectorRef);

    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() readOnlyMode = false;
    @Input() previewMode: boolean;
    // if the post is previewed in the create/edit modal,
    // we need to pass the ref in order to close it when navigating to the previewed post via post context
    @Input() modalRef?: NgbModalRef;
    tags: string[];
    courseId: number;
    @Input()
    hasChannelModerationRights = false;

    @ViewChild(AnswerPostCreateEditModalComponent) answerPostCreateEditModal?: AnswerPostCreateEditModalComponent;
    @Input() showAnswers: boolean;
    @Input() isCommunicationPage: boolean;
    @Input() sortedAnswerPosts: AnswerPost[];
    @Output() openThread = new EventEmitter<void>();
    @Output() userReferenceClicked = new EventEmitter<string>();
    @Output() channelReferenceClicked = new EventEmitter<number>();

    createdAnswerPost: AnswerPost;
    isAtLeastTutorInCourse: boolean;

    // ng-container to render createEditAnswerPostComponent
    @ViewChild('createEditAnswerPostContainer', { read: ViewContainerRef }) containerRef: ViewContainerRef;
    @ViewChild('createAnswerPostModal') createAnswerPostModalComponent: AnswerPostCreateEditModalComponent;

    /**
     * on initialization: updates the post tags and the context information
     */
    ngOnInit(): void {
        this.courseId = this.metisService.getCourse().id!;
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.createdAnswerPost = this.createEmptyAnswerPost();
    }

    /**
     * on leaving the page, the container for answerPost creation or editing should be cleared
     */
    ngOnDestroy(): void {
        this.answerPostCreateEditModal?.createEditAnswerPostContainerRef?.clear();
    }

    /**
     * this lifecycle hook is required to avoid causing "Expression has changed after it was checked"-error when dismissing all changes in the tag-selector
     * on dismissing the edit-create-modal -> we do not want to store changes in the create-edit-modal that are not saved
     */
    ngAfterContentChecked() {
        this.changeDetector.detectChanges();
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
     * Open create answer modal
     */
    openCreateAnswerPostModal() {
        this.createAnswerPostModalComponent.open();
    }
}
