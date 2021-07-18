import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostService } from 'app/shared/metis/post/post.service';
import { PostingDirective } from 'app/shared/metis/posting.directive';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-post',
    templateUrl: './post.component.html',
    styleUrls: ['../../../overview/discussion/discussion.scss'],
})
export class PostComponent extends PostingDirective<Post> implements OnInit {
    @Output() toggleAnswersChange: EventEmitter<void> = new EventEmitter<void>();

    constructor(protected postService: PostService, protected metisService: MetisService) {
        super();
    }
}
