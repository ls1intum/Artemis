import { Component } from '@angular/core';
import { PostingsCreateEditModalDirective } from 'app/shared/metis/postings-create-edit-modal/postings-create-edit-modal.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { AnswerPostService } from 'app/shared/metis/answer-post/answer-post.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-answer-post-create-edit-modal',
    templateUrl: './answer-post-create-edit-modal.component.html',
})
export class AnswerPostCreateEditModalComponent extends PostingsCreateEditModalDirective<AnswerPost> {
    constructor(protected answerPostService: AnswerPostService, protected modalService: NgbModal) {
        super(answerPostService, modalService);
    }
}
