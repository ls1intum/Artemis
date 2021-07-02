import { Component } from '@angular/core';
import { PostingsFooterDirective } from 'app/shared/metis/postings-footer/postings-footer.component';
import { Post } from 'app/entities/metis/post.model';
import { PostService } from 'app/shared/metis/post/post.service';

@Component({
    selector: 'jhi-post-footer',
    templateUrl: './post-footer.component.html',
})
export class PostFooterComponent extends PostingsFooterDirective<Post> {
    constructor(protected postService: PostService) {
        super(postService);
    }
}
