import { Component, Input, OnDestroy, ViewChild } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingHeaderDirective } from 'app/shared/metis/posting-header/posting-header.directive';
import { MetisService } from 'app/shared/metis/metis.service';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';

@Component({
    selector: 'jhi-post-header',
    templateUrl: './post-header.component.html',
    styleUrls: ['../../metis.component.scss'],
})
export class PostHeaderComponent extends PostingHeaderDirective<Post> implements OnDestroy {
    @Input() previewMode: boolean;
    @ViewChild(PostCreateEditModalComponent) postCreateEditModal?: PostCreateEditModalComponent;

    constructor(protected metisService: MetisService) {
        super(metisService);
    }

    /**
     * on leaving the page, the modal should be closed
     */
    ngOnDestroy(): void {
        this.postCreateEditModal?.modalRef?.close();
    }

    /**
     * invokes the metis service to delete a post
     */
    deletePosting(): void {
        this.metisService.deletePost(this.posting);
    }
}
