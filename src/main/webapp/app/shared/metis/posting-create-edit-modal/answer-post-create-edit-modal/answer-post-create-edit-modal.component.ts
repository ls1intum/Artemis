import { Component } from '@angular/core';
import { PostingCreateEditModalDirective } from 'app/shared/metis/posting-create-edit-modal/posting-create-edit-modal.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MetisService } from 'app/shared/metis/metis.service';
import { FormBuilder, Validators } from '@angular/forms';

@Component({
    selector: 'jhi-answer-post-create-edit-modal',
    templateUrl: './answer-post-create-edit-modal.component.html',
})
export class AnswerPostCreateEditModalComponent extends PostingCreateEditModalDirective<AnswerPost> {
    constructor(protected metisService: MetisService, protected modalService: NgbModal, protected formBuilder: FormBuilder) {
        super(metisService, modalService, formBuilder);
    }

    /**
     * resets the answer post content
     */
    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            // the pattern ensures that the content must include at least one non-whitespace character
            content: [this.posting.content, [Validators.required, Validators.maxLength(this.maxContentLength), Validators.pattern(/^(\n|.)*\S+(\n|.)*$/)]],
        });
    }

    /**
     * invokes the metis service after setting current date as creation date of the new answer post,
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    createPosting(): void {
        this.posting.content = this.formGroup.get('content')?.value;
        this.metisService.createAnswerPost(this.posting).subscribe({
            next: (answerPost: AnswerPost) => {
                this.resetFormGroup();
                this.isLoading = false;
                this.onCreate.emit(answerPost);
                this.modalRef?.close();
            },
            error: () => {
                this.isLoading = false;
            },
        });
    }

    /**
     * invokes the metis service with the updated answer post
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    updatePosting(): void {
        this.posting.content = this.formGroup.get('content')?.value;
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

    /**
     * updates the title in accordance with the current use case (edit or create)
     */
    updateModalTitle(): void {
        if (this.editType === this.EditType.UPDATE) {
            this.modalTitle = 'artemisApp.metis.editPosting';
        } else if (this.editType === this.EditType.CREATE) {
            this.modalTitle = 'artemisApp.metis.createModalTitleAnswer';
        }
    }
}
