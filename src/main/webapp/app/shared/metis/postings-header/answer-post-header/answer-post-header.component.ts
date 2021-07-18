import { Component } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingsHeaderDirective } from 'app/shared/metis/postings-header/postings-header.directive';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-answer-post-header',
    templateUrl: './answer-post-header.component.html',
    styleUrls: ['../../../../overview/discussion/discussion.scss'],
})
export class AnswerPostHeaderComponent extends PostingsHeaderDirective<AnswerPost> {
    constructor(protected metisService: MetisService) {
        super(metisService);
    }

    deletePosting(): void {
        this.metisService.deleteAnswerPost(this.posting);
    }
}
