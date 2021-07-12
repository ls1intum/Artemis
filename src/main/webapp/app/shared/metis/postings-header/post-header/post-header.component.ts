import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingsHeaderDirective } from 'app/shared/metis/postings-header/postings-header.directive';
import { PostService } from 'app/shared/metis/post/post.service';

@Component({
    selector: 'jhi-post-header',
    templateUrl: './post-header.component.html',
    styleUrls: ['../../../../overview/discussion/discussion.scss'],
})
export class PostHeaderComponent extends PostingsHeaderDirective<Post> {
    @Input() existingPostTags: string[];
    @Output() toggledAnswersChange: EventEmitter<void> = new EventEmitter<void>();

    constructor(protected postService: PostService) {
        super(postService);
    }

    getNumberOfAnswerPosts(): number {
        return <number>this.posting.answers?.length! ? this.posting.answers?.length! : 0;
    }

    toggleAnswers(): void {
        this.toggledAnswersChange.emit();
    }
}
