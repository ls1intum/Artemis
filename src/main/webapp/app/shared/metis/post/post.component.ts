import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import { MetisService } from 'app/shared/metis/metis.service';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-post',
    templateUrl: './post.component.html',
    styleUrls: ['./post.component.scss'],
})
export class PostComponent extends PostingDirective<Post> implements OnInit, OnChanges {
    @Output() toggleAnswersChange: EventEmitter<void> = new EventEmitter<void>();
    @Input() previewMode: boolean;
    // if the post is previewed in the create/edit modal, we need to pass the ref in order to close it when navigating to the previewed post
    @Input() modalRef?: NgbModalRef;
    postIsResolved: boolean;

    constructor(public metisService: MetisService) {
        super();
    }

    ngOnInit() {
        super.ngOnInit();
        this.postIsResolved = this.metisService.isPostResolved(this.posting);
    }

    ngOnChanges() {
        this.postIsResolved = this.metisService.isPostResolved(this.posting);
    }

    /**
     * on clicking the post id anchor and navigate to a post previewed in the similar post section, we need to close the create-edit-modal
     */
    closeRef(): void {
        this.modalRef?.close();
    }
}
