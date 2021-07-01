import { Component } from '@angular/core';
import { PostingsCreateEditModalDirective } from 'app/shared/metis/postings-create-edit-modal/postings-create-edit-modal.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostService } from 'app/shared/metis/post/post.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-post-create-edit-modal',
    templateUrl: './post-create-edit-modal.component.html',
})
export class PostCreateEditModalComponent extends PostingsCreateEditModalDirective<AnswerPost> {
    constructor(protected postService: PostService, protected modalService: NgbModal) {
        super(postService, modalService);
    }
}
