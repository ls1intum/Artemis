import { Component, Input } from '@angular/core';
import { PostingsCreateEditModalDirective } from 'app/shared/metis/postings-create-edit-modal/postings-create-edit-modal.directive';
import { PostService } from 'app/shared/metis/post/post.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Post } from 'app/entities/metis/post.model';

@Component({
    selector: 'jhi-post-create-edit-modal',
    templateUrl: './post-create-edit-modal.component.html',
})
export class PostCreateEditModalComponent extends PostingsCreateEditModalDirective<Post> {
    @Input() existingPostTags: string[];

    constructor(protected postService: PostService, protected modalService: NgbModal) {
        super(postService, modalService);
    }

    updateTags(tags: string[]): void {
        this.posting.tags = tags;
    }
}
