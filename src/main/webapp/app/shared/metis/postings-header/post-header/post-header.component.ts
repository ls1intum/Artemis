import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Post } from 'app/entities/metis/post.model';
import { PostingsHeaderDirective } from 'app/shared/metis/postings-header/postings-header.directive';
import { PostService } from 'app/shared/metis/post/post.service';

@Component({
    selector: 'jhi-post-header',
    templateUrl: './post-header.component.html',
    styleUrls: ['../../../../overview/discussion/discussion.scss'],
})
export class PostHeaderComponent extends PostingsHeaderDirective<Post> {
    constructor(protected postService: PostService, protected route: ActivatedRoute) {
        super(postService, route);
    }

    getNumberOfAnswerPosts(): number {
        return <number>this.posting.answers?.length! ? this.posting.answers?.length! : 0;
    }
}
