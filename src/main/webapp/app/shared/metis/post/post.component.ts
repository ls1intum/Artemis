import { Component, EventEmitter, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingDirective } from 'app/shared/metis/posting.directive';

@Component({
    selector: 'jhi-post',
    templateUrl: './post.component.html',
    styleUrls: ['./post.component.scss'],
})
export class PostComponent extends PostingDirective<Post> {
    @Output() toggleAnswersChange: EventEmitter<void> = new EventEmitter<void>();
}
