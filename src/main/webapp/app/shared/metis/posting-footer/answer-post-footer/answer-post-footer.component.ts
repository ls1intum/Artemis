import { Component } from '@angular/core';
import { PostingFooterDirective } from 'app/shared/metis/posting-footer/posting-footer.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

@Component({
    selector: 'jhi-answer-post-footer',
    templateUrl: './answer-post-footer.component.html',
    styleUrls: ['./answer-post-footer.component.scss'],
})
export class AnswerPostFooterComponent extends PostingFooterDirective<AnswerPost> {}
