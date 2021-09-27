import { Component, OnChanges, OnInit } from '@angular/core';
import { PostingsFooterDirective } from 'app/shared/metis/postings-footer/postings-footer.directive';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { ContextInformation, PageType } from 'app/shared/metis/metis.util';

@Component({
    selector: 'jhi-post-footer',
    templateUrl: './post-footer.component.html',
    styleUrls: ['./post-footer.component.scss'],
})
export class PostFooterComponent extends PostingsFooterDirective<Post> implements OnInit, OnChanges {
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
}
