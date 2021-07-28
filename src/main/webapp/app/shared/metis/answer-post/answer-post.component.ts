import { Component } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingDirective } from '../posting.directive';

@Component({
    selector: 'jhi-answer-post',
    templateUrl: './answer-post.component.html',
    styleUrls: ['./answer-post.scss'],
})
export class AnswerPostComponent extends PostingDirective<AnswerPost> {}
