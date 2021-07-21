import { Component, OnChanges, OnInit } from '@angular/core';
import { PostingsFooterDirective } from 'app/shared/metis/postings-footer/postings-footer.directive';
import { Post } from 'app/entities/metis/post.model';

@Component({
    selector: 'jhi-post-footer',
    templateUrl: './post-footer.component.html',
    styleUrls: ['../../../../overview/discussion/discussion.scss'],
})
export class PostFooterComponent extends PostingsFooterDirective<Post> implements OnInit, OnChanges {
    tags: string[];

    constructor() {
        super();
    }

    ngOnInit(): void {
        this.updateTags();
    }

    ngOnChanges(): void {
        this.updateTags();
    }

    private updateTags() {
        if (this.posting.tags) {
            this.tags = this.posting.tags;
        } else {
            this.tags = [];
        }
    }
}
