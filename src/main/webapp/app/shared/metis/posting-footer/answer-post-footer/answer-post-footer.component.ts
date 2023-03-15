import { Component, EventEmitter, Input, Output } from '@angular/core';

import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingFooterDirective } from 'app/shared/metis/posting-footer/posting-footer.directive';

@Component({
    selector: 'jhi-answer-post-footer',
    templateUrl: './answer-post-footer.component.html',
    styleUrls: ['./answer-post-footer.component.scss'],
})
export class AnswerPostFooterComponent extends PostingFooterDirective<AnswerPost> {
    @Input()
    isReadOnlyMode = false;
    @Input() isLastAnswer = false;
    @Output() openPostingCreateEditModal = new EventEmitter<void>();
}
