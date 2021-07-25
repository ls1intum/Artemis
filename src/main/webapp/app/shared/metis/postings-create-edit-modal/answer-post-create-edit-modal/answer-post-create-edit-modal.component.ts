import { Component } from '@angular/core';
import { PostingsCreateEditModalDirective } from 'app/shared/metis/postings-create-edit-modal/postings-create-edit-modal.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MetisService } from 'app/shared/metis/metis.service';
import * as moment from 'moment';
import { FormBuilder, Validators } from '@angular/forms';

@Component({
    selector: 'jhi-answer-post-create-edit-modal',
    templateUrl: './answer-post-create-edit-modal.component.html',
})
export class AnswerPostCreateEditModalComponent extends PostingsCreateEditModalDirective<AnswerPost> {
    constructor(protected metisService: MetisService, protected modalService: NgbModal, protected formBuilder: FormBuilder) {
        super(metisService, modalService, formBuilder);
    }

    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            content: [this.posting.content, [Validators.required, Validators.maxLength(this.maxContentLength)]],
        });
    }

    createPosting(): void {
        this.posting.creationDate = moment();
        this.metisService.createAnswerPost(this.posting).subscribe({
            next: (answerPost: AnswerPost) => {
                this.isLoading = false;
                this.onCreate.emit(answerPost);
                this.modalRef?.close();
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
