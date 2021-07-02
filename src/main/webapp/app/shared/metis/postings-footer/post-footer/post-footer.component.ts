import { Component, OnInit } from '@angular/core';
import { PostingsFooterDirective } from 'app/shared/metis/postings-footer/postings-footer.component';
import { Post } from 'app/entities/metis/post.model';
import { PostService } from 'app/shared/metis/post/post.service';

@Component({
    selector: 'jhi-post-footer',
    templateUrl: './post-footer.component.html',
    styleUrls: ['../../../../overview/discussion/discussion.scss'],
})
export class PostFooterComponent extends PostingsFooterDirective<Post> implements OnInit {
    tags: string[];

    constructor(protected postService: PostService) {
        super(postService);
    }

    ngOnInit() {
        if (this.posting.tags) {
            this.tags = this.posting.tags;
        } else {
            this.tags = [];
        }
    }
}
