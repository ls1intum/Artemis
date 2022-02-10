import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { PostingFooterDirective } from 'app/shared/metis/posting-footer/posting-footer.directive';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-post-footer',
    templateUrl: './post-footer.component.html',
    styleUrls: ['./post-footer.component.scss'],
})
export class PostFooterComponent extends PostingFooterDirective<Post> implements OnInit, OnChanges {
    @Input() previewMode: boolean;
    // if the post is previewed in the create/edit modal,
    // we need to pass the ref in order to close it when navigating to the previewed post via post context
    @Input() modalRef?: NgbModalRef;
    tags: string[];
    courseId: number;

    constructor(private metisService: MetisService) {
        super();
    }

    /**
     * on initialization: updates the post tags and the context information
     */
    ngOnInit(): void {
        this.courseId = this.metisService.getCourse().id!;
        this.updateTags();
    }

    /**
     * on changes: updates the post tags and the context information
     */
    ngOnChanges(): void {
        this.updateTags();
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
}
