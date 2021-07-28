import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';

@Component({
    selector: 'jhi-post',
    templateUrl: './post.component.html',
    styleUrls: ['../../../overview/discussion/discussion.scss'],
})
export class PostComponent extends PostingDirective<Post> implements OnInit {
    @Output() toggleAnswersChange: EventEmitter<void> = new EventEmitter<void>();
}
