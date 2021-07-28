import { Component, EventEmitter, OnChanges, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingsHeaderDirective } from 'app/shared/metis/postings-header/postings-header.directive';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-post-header',
    templateUrl: './post-header.component.html',
    styleUrls: ['../posting-header.component.scss'],
})
export class PostHeaderComponent extends PostingsHeaderDirective<Post> implements OnChanges {
    @Output() toggleAnswersChange: EventEmitter<void> = new EventEmitter<void>();
    numberOfAnswerPosts: number;

    constructor(protected metisService: MetisService) {
        super(metisService);
    }

    /**
     * on changes: updates the number of answer posts
     */
    ngOnChanges(): void {
        this.numberOfAnswerPosts = this.getNumberOfAnswerPosts();
    }

    /**
     * counts the answer posts of a post, 0 if none exist
     * @return number number of answer posts
     */
    getNumberOfAnswerPosts(): number {
        return <number>this.posting.answers?.length! ? this.posting.answers?.length! : 0;
    }

    /**
     * emits an event of toggling (show, do not show) the answer posts for a post
     */
    toggleAnswers(): void {
        this.toggleAnswersChange.emit();
    }

    /**
     * invokes the metis service to delete a post
     */
    deletePosting(): void {
        this.metisService.deletePost(this.posting);
    }
}
