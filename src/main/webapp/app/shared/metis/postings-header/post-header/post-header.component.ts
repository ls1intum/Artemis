import { Component, EventEmitter, OnChanges, OnDestroy, Output, ViewChild } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingsHeaderDirective } from 'app/shared/metis/postings-header/postings-header.directive';
import { MetisService } from 'app/shared/metis/metis.service';
import { PostCreateEditModalComponent } from 'app/shared/metis/postings-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';

@Component({
    selector: 'jhi-post-header',
    templateUrl: './post-header.component.html',
    styleUrls: ['../posting-header.component.scss'],
})
export class PostHeaderComponent extends PostingsHeaderDirective<Post> implements OnChanges, OnDestroy {
    @Output() toggleAnswersChange: EventEmitter<void> = new EventEmitter<void>();
    @ViewChild(PostCreateEditModalComponent) postCreateEditModal?: PostCreateEditModalComponent;
    numberOfAnswerPosts: number;
    showAnswers = false;

    constructor(protected metisService: MetisService) {
        super(metisService);
    }

    /**
     * on changes: updates the number of answer posts
     */
    ngOnChanges(): void {
        this.numberOfAnswerPosts = this.getNumberOfAnswerPosts();
    }

    /**
     * on leaving the page, the modal should be closed
     */
    ngOnDestroy(): void {
        this.postCreateEditModal?.modalRef?.close();
    }

    /**
     * counts the answer posts of a post, 0 if none exist
     * @return number number of answer posts
     */
    getNumberOfAnswerPosts(): number {
        return <number>this.posting.answers?.length! ? this.posting.answers?.length! : 0;
    }

    /**
     * toggles showAnswers flag to highlight header icon when answers are toggled
     * emits an event of toggling (show, do not show) the answer posts for a post
     */
    toggleAnswers(): void {
        if (this.getNumberOfAnswerPosts() > 0) {
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
