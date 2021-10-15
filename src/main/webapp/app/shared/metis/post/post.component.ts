import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import { MetisService } from 'app/shared/metis/metis.service';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { CourseWideContext } from '../metis.util';

@Component({
    selector: 'jhi-post',
    templateUrl: './post.component.html',
    styleUrls: ['./post.component.scss', './../metis.component.scss'],
})
export class PostComponent extends PostingDirective<Post> implements OnInit, OnChanges {
    @Output() toggleAnswersChange: EventEmitter<void> = new EventEmitter<void>();
    @Input() previewMode: boolean;
    // if the post is previewed in the create/edit modal,
    // we need to pass the ref in order to close it when navigating to the previewed post via post title
    @Input() modalRef?: NgbModalRef;
    postIsResolved: boolean;
    readonly CourseWideContext = CourseWideContext;

    constructor(public metisService: MetisService) {
        super();
    }

    /**
     * on initialization: invokes the metis service to evaluate, if the post is already solved
     */
    ngOnInit() {
        super.ngOnInit();
        this.postIsResolved = this.metisService.isPostResolved(this.posting);
    }

    /**
     * on changed: re-evaluates, if the post is already resolved by one of the given answers
     */
    ngOnChanges() {
        this.postIsResolved = this.metisService.isPostResolved(this.posting);
    }

    /**
     * ensures that only when clicking on a post title without having cmd key pressed,
     * the modal is dismissed (closed and cleared)
     */
    onNavigateToPostById($event: MouseEvent) {
        if (!$event.metaKey) {
            this.modalRef?.dismiss();
        }
    }
}
