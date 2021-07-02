import { Component } from '@angular/core';
import { AnswerPostService } from 'app/shared/metis/answer-post/answer-post.service';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingsHeaderDirective } from 'app/shared/metis/postings-header/postings-header.directive';

@Component({
    selector: 'jhi-answer-post-header',
    templateUrl: './answer-post-header.component.html',
    styleUrls: ['../../../../overview/discussion/discussion.scss'],
})
export class AnswerPostHeaderComponent extends PostingsHeaderDirective<AnswerPost> {
    constructor(protected answerPostService: AnswerPostService) {
        super(answerPostService);
    }
}
