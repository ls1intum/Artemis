import { Component, OnChanges, OnInit } from '@angular/core';
import { PostingsFooterDirective } from 'app/shared/metis/postings-footer/postings-footer.directive';
import { Post } from 'app/entities/metis/post.model';
import { PostService } from 'app/shared/metis/post/post.service';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-post-footer',
    templateUrl: './post-footer.component.html',
    styleUrls: ['../../../../overview/discussion/discussion.scss'],
})
export class PostFooterComponent extends PostingsFooterDirective<Post> implements OnInit, OnChanges {
    tags: string[];

    constructor(protected postService: PostService, protected metisService: MetisService) {
        super(postService, metisService);
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
