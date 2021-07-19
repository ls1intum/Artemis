import { Component, EventEmitter, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingsHeaderDirective } from 'app/shared/metis/postings-header/postings-header.directive';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-post-header',
    templateUrl: './post-header.component.html',
    styleUrls: ['../../../../overview/discussion/discussion.scss'],
})
export class PostHeaderComponent extends PostingsHeaderDirective<Post> {
    @Output() toggleAnswersChange: EventEmitter<void> = new EventEmitter<void>();

    constructor(protected metisService: MetisService) {
        super(metisService);
    }

    getNumberOfAnswerPosts(): number {
        return <number>this.posting.answers?.length! ? this.posting.answers?.length! : 0;
    }

    toggleAnswers(): void {
        this.toggleAnswersChange.emit();
    }

    deletePosting(): void {
        this.metisService.deletePost(this.posting);
    }
}
