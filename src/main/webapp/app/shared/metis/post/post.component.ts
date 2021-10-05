import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import { MetisService } from 'app/shared/metis/metis.service';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-post',
    templateUrl: './post.component.html',
    styleUrls: ['./post.component.scss'],
})
export class PostComponent extends PostingDirective<Post> {
    @Output() toggleAnswersChange: EventEmitter<void> = new EventEmitter<void>();
    @Input() previewMode: boolean;
    // if the post is previewed in the create/edit modal, we need to pass the ref in order to close it when navigating to the previewed post
    @Input() modalRef?: NgbModalRef;

    constructor(public metisService: MetisService) {
        super();
    }

    /**
     * on clicking the post id anchor and navigate to a post previewed in the similar post section, we need to close the create-edit-modal
     */
    closeRef(): void {
        this.modalRef?.close();
    }
}
