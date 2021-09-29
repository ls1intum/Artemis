import { Component } from '@angular/core';
import { PostingsFooterDirective } from 'app/shared/metis/postings-footer/postings-footer.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

@Component({
    selector: 'jhi-answer-post-footer',
    templateUrl: './answer-post-footer.component.html',
    styleUrls: ['./answer-post-footer.component.scss'],
})
export class AnswerPostFooterComponent extends PostingsFooterDirective<AnswerPost> {}
