import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { PostingsFooterDirective } from 'app/shared/metis/postings-footer/postings-footer.directive';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { ContextInformation, PageType } from 'app/shared/metis/metis.util';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-post-footer',
    templateUrl: './post-footer.component.html',
    styleUrls: ['./post-footer.component.scss'],
})
export class PostFooterComponent extends PostingsFooterDirective<Post> implements OnInit, OnChanges {
    @Input() previewMode: boolean;
    // if the post is previewed in the create/edit modal,
    // we need to pass the ref in order to close it when navigating to the previewed post via post context
    @Input() modalRef?: NgbModalRef;
    tags: string[];
    pageType: PageType;
    contextInformation: ContextInformation;
    courseId: number;
    readonly PageType = PageType;

    constructor(private metisService: MetisService) {
        super();
    }

    /**
     * on initialization: updates the post tags and the context information
     */
    ngOnInit(): void {
        this.pageType = this.metisService.getPageType();
        this.courseId = this.metisService.getCourse().id!;
        this.updateTags();
        this.contextInformation = this.metisService.getContextInformation(this.posting);
    }

    /**
     * on changes: updates the post tags and the context information
     */
    ngOnChanges(): void {
        this.updateTags();
        this.contextInformation = this.metisService.getContextInformation(this.posting);
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

    /**
     * ensures that only when clicking on context without having control key pressed,
     * the modal is dismissed (closed and cleared)
     */
    onNavigateToContext($event: MouseEvent) {
        if (!$event.metaKey) {
            this.modalRef?.dismiss();
        }
    }
}
