import { Component, EventEmitter, OnInit, OnDestroy, Output, ViewChild, OnChanges } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingsHeaderDirective } from 'app/shared/metis/postings-header/postings-header.directive';
import { MetisService } from 'app/shared/metis/metis.service';
import { PostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';

@Component({
    selector: 'jhi-post-header',
    templateUrl: './post-header.component.html',
    styleUrls: ['../posting-header.component.scss'],
})
export class PostHeaderComponent extends PostingsHeaderDirective<Post> implements OnInit, OnChanges, OnDestroy {
    @Output() toggleAnswersChange: EventEmitter<void> = new EventEmitter<void>();
    @ViewChild(PostCreateEditModalComponent) postCreateEditModal?: PostCreateEditModalComponent;
    numberOfAnswerPosts: number;
    postIsResolved: boolean;
    showAnswers = false;

    constructor(protected metisService: MetisService) {
        super(metisService);
    }

    /**
     * on initialization: updates answer post information
     */
    ngOnInit(): void {
        this.numberOfAnswerPosts = this.metisService.getNumberOfAnswerPosts(this.posting);
        this.postIsResolved = this.metisService.isPostResolved(this.posting);
        super.ngOnInit();
    }

    /**
     * on changes: updates answer post information
     */
    ngOnChanges(): void {
        this.numberOfAnswerPosts = this.metisService.getNumberOfAnswerPosts(this.posting);
        this.postIsResolved = this.metisService.isPostResolved(this.posting);
    }

    /**
     * on leaving the page, the modal should be closed
     */
    ngOnDestroy(): void {
        this.postCreateEditModal?.modalRef?.close();
    }

    /**
     * toggles showAnswers flag to highlight header icon when answers are toggled
     * emits an event of toggling (show, do not show) the answer posts for a post
     */
    toggleAnswers(): void {
        if (this.numberOfAnswerPosts > 0) {
            this.showAnswers = !this.showAnswers;
        }
        this.toggleAnswersChange.emit();
    }

    /**
     * invokes the metis service to delete a post
     */
    deletePosting(): void {
        this.metisService.deletePost(this.posting);
    }
}
