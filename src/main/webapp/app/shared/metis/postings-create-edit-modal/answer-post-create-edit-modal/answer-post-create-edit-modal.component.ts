import { Component } from '@angular/core';
import { PostingsCreateEditModalDirective } from 'app/shared/metis/postings-create-edit-modal/postings-create-edit-modal.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MetisService } from 'app/shared/metis/metis.service';
import moment from 'moment';

@Component({
    selector: 'jhi-answer-post-create-edit-modal',
    templateUrl: './answer-post-create-edit-modal.component.html',
})
export class AnswerPostCreateEditModalComponent extends PostingsCreateEditModalDirective<AnswerPost> {
    constructor(protected metisService: MetisService, protected modalService: NgbModal) {
        super(metisService, modalService);
    }

    createPosting(): void {
        this.posting.creationDate = moment();
        this.metisService.createAnswerPost(this.posting).subscribe({
            next: (answerPost: AnswerPost) => {
                this.isLoading = false;
                this.modalRef?.close();
                this.onCreate.emit(answerPost);
            },
            error: () => {
                this.isLoading = false;
            },
        });
    }

    updatePosting(): void {
        this.metisService.updateAnswerPost(this.posting).subscribe({
            next: () => {
                this.isLoading = false;
                this.modalRef?.close();
            },
            error: () => {
                this.isLoading = false;
            },
        });
    }

    updateModalTitle() {
        if (this.posting.id) {
            this.modalTitle = 'artemisApp.metis.editPosting';
        } else {
            this.modalTitle = 'artemisApp.metis.createModalTitleAnswer';
        }
    }
}
